# 05 — Especificação inicial da API

## 1. Convenções

- Base path: `/api/v1`.
- JSON UTF-8.
- UUID em identificadores.
- Datas em ISO 8601.
- Paginação com `page`, `size` e `sort`.
- Erros em formato consistente.
- Autenticação via sessão/token seguro conforme implementação.
- Organização resolvida no contexto autenticado, nunca confiada ao corpo da requisição.

Exemplo de erro:

```json
{
  "type": "VALIDATION_ERROR",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/companies",
  "errors": [
    {
      "field": "name",
      "message": "must not be blank"
    }
  ]
}
```

## 2. Autenticação

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/auth/login` | Autenticar |
| POST | `/auth/logout` | Encerrar sessão, se aplicável |
| POST | `/auth/refresh` | Renovar credencial, se adotado |
| GET | `/auth/me` | Usuário e organização ativa |
| POST | `/auth/password` | Trocar a própria senha (D28) |

Gestão de membros (D28), somente `ADMIN`:

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/organizations/members?includeRevoked=` | Listar membros |
| GET | `/organizations/members/{memberId}` | Detalhar membro |
| POST | `/organizations/members` | Cadastrar; resposta traz a senha temporária, só nesta vez |
| PATCH | `/organizations/members/{memberId}` | Mudar papel e/ou acesso (`role`, `status`) |
| POST | `/organizations/members/{memberId}/reset-password` | Nova senha temporária |

`/auth/refresh` não existe (D1). Com senha temporária, a sessão só acessa
`/auth/me`, `/auth/logout` e `/auth/password`; o resto responde `403
PASSWORD_CHANGE_REQUIRED`. Senha atual errada responde `400
INVALID_CREDENTIALS`, e não 401, porque a sessão continua válida.

Não armazenar tokens de longa duração em `localStorage` sem avaliação de risco. Definir estratégia de refresh, cookies `HttpOnly`, `Secure`, `SameSite` e proteção CSRF conforme arquitetura de autenticação escolhida.

## 3. Dashboard

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/dashboard/summary` | Indicadores gerais |
| GET | `/dashboard/pipeline` | Contagem por etapa |
| GET | `/dashboard/activity` | Atividades recentes |
| GET | `/dashboard/conversion` | Conversão por origem e segmento (RF-071) |
| GET | `/dashboard/timeline` | Métricas do período por dia, semana ou mês |
| GET | `/dashboard/attention` | O que pede ação agora (D30) |
| GET | `/dashboard/funnel` | Alcance por etapa e tempo médio nela (D30) |

Parâmetros opcionais, iguais em todas as rotas: `from`, `to`, `segment`,
`city`, `source` (`MANUAL`, `CSV_IMPORT`, `INTEGRATION`; origem da empresa,
D29) e `assignee` (id do responsável do lead, D30).

Implementado na Etapa 7 (D25): `summary` traz `leadsByStatus` (foto atual),
`activeLeads`, `companiesAdded`, `contactsRegistered`, `openProposals`,
`dealsWon`, `wonValue`, `openTasks` e `overdueTasks`, com o período efetivo
(`from`/`to`, datas de Brasília, padrão últimos 30 dias). `activity` é paginada.
A contagem por etapa (`/dashboard/pipeline`) está dentro de `summary`.

`conversion` (D29) traz, para os leads abertos no período, `bySource` e
`bySegment` (até 10 segmentos) com `leads`, `contacted`, `won` e
`sufficientSample`, além de `minimumSample`. Sem amostra suficiente a tela não
mostra percentual.

`timeline` (D29) aceita também `granularity` (`DAY`, `WEEK`, `MONTH`; sem ele,
até 31 dias é por dia, até 120 por semana e acima disso por mês) e devolve
`points` com `start`, `companiesAdded`, `contactsRegistered`, `dealsWon` e
`wonValue` para **todo** intervalo do período, inclusive os vazios.

`summary` traz também `companiesWithoutSegment` (empresas ativas sem segmento).
`attention` (D30; `limit` de 1 a 20, padrão 5) traz `tasks`, `staleLeads`,
`quietProposals` e `withoutTask`, cada um com `total` e `items`
(`leadId`, `companyName`, `status`, `at`, `detail`, `overdue`); é a situação de
agora, sem período. `funnel` traz `cohort`, `minimumSample` e `stages`
(`status`, `reached`, `exits`, `avgDays`, `sufficientSample`).

`GET /team` (qualquer membro): membros ativos só com `userId` e `displayName`,
para atribuir lead (`PATCH /leads/{id}` com `assignedUserId`) e filtrar por
responsável. E-mail e papel ficam em `/organizations/members`, do administrador.

## 4. Empresas

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/companies` | Criar empresa |
| GET | `/companies` | Listar e filtrar |
| GET | `/companies/{companyId}` | Detalhar |
| PATCH | `/companies/{companyId}` | Atualizar |
| POST | `/companies/{companyId}/archive` | Arquivar |
| GET | `/companies/{companyId}/sources` | Histórico de origem (RF-022), paginado |
| GET | `/companies/{companyId}/duplicates` | Possíveis duplicatas (RF-014) |
| GET | `/companies/worklist` | Lista como ferramenta: lead atual, contato, origem, atividade, duplicata (D32) |
| GET | `/companies/{companyId}/summary` | Uma linha da lista, para prévia e cabeçalho do detalhe |
| GET | `/companies/{companyId}/leads` | Ciclos comerciais da empresa, paginado |
| POST | `/companies/duplicate-check` | Possíveis duplicatas enquanto o cadastro é digitado |
| POST | `/companies/bulk` | Segmento ou cidade/UF em até 100 empresas |
| POST | `/companies/{companyId}/merge` | Mesclar duplicata nesta empresa (D32) |
| GET | `/companies/{companyId}/removal` | Se a exclusão seria aceita agora (D32) |
| DELETE | `/companies/{companyId}` | Excluir empresa sem atividade (D32) |

Exemplo de criação:

```json
{
  "name": "Empresa Exemplo",
  "tradeName": "Exemplo",
  "segment": "BARBERSHOP",
  "city": "Poços de Caldas",
  "state": "MG",
  "websiteUrl": null,
  "instagramUrl": null,
  "phone": null,
  "source": {
    "name": "Pesquisa no mapa",
    "url": "https://exemplo.test/local",
    "reference": null,
    "collectedAt": "2026-11-05T12:00:00Z",
    "note": null
  }
}
```

Contrato implementado na Etapa 4:

- **Origem.** `source` é opcional; ausente, registra-se "Cadastro manual" com
  a data atual. Por esta rota o tipo é sempre `MANUAL`: o cliente não informa
  tipo, para não registrar como importação um dado digitado à mão.
  `collectedAt` no futuro é recusado.
- **URLs** (`websiteUrl`, `instagramUrl`, `source.url`) aceitam `http`, `https`
  ou nenhum esquema. Qualquer outro esquema (`javascript:`, `data:`…) é
  recusado com `VALIDATION_ERROR`, porque a URL vira link na interface.
- **PATCH parcial.** Campo ausente mantém o valor; texto vazio limpa um campo
  opcional. `name`, `city` e `state` não podem ser limpos. Empresa arquivada
  responde `409 CONFLICT`.
- **Listagem.** Filtros `search` (nome, ignorando acento e caixa), `segment`,
  `city`, `state`, `includeArchived` (padrão `false`). Ordenação `sort=campo,asc|desc`
  com `name`, `city`, `createdAt`, `updatedAt`; outro campo responde `400`.
  `size` máximo 100.
- **Duplicatas.** Sinais `DOMAIN`, `PHONE` e `NAME_CITY_STATE` (nome
  normalizado idêntico na mesma cidade e estado), no máximo dez por sinal,
  sempre da mesma organização. É sugestão para revisão: nada é mesclado.
- A resposta nunca expõe `organizationId` nem as colunas normalizadas.

Contrato das ferramentas de empresa (D32):

- **Lista** (`/companies/worklist`). Filtros `search`, `segment` ou
  `noSegment=true`, `city`, `source` (`MANUAL`, `CSV_IMPORT`, `INTEGRATION`:
  a primeira proveniência, D29) e `focus`: `WITHOUT_LEAD` (sem lead aberto),
  `WITHOUT_CONTACT` (sem telefone, e-mail, site nem Instagram), `DUPLICATE`
  (outra empresa **ativa** com o mesmo domínio, telefone, ou nome na mesma
  cidade e UF) e `ARCHIVED`; sem `ARCHIVED`, só ativas. `sort` `NAME`, `CITY`,
  `CREATED` (padrão) ou `LAST_ACTIVITY`, `direction` `asc|desc`, nulos por
  último. Cada item traz `currentLead` (o aberto; sem ele, o mais recente,
  com `open: false`), `hasPhone`/`hasEmail`/`hasWebsite`/`hasInstagram`,
  `origin`, `leadCount`, `lastActivityAt` (última interação ou mudança de
  etapa em qualquer ciclo) e `possibleDuplicate`.
- **Duplicata ao digitar** (`POST`, para telefone e site não irem à URL):
  `{name, city, state, websiteUrl, phone, excludeCompanyId}`, todos opcionais.
  Nome só conta com cidade e UF. Mesmos sinais e limites de `/duplicates`.
- **Lote:** `{"companyIds": [...], "segment": "..."}` ou com `"city"` e
  `"state"` juntos. Segmento na grafia já existente (D31). Arquivadas não
  mudam e voltam em `skippedArchived`; um id de outra organização responde
  `404` e nada muda.
- **Mesclar:** `{"sourceCompanyId": "..."}`. Leads, proveniência e linhas de
  importação passam para a empresa da rota; campos vazios dela são
  completados com os da origem, sem sobrescrever; a origem é arquivada.
  Resposta `{company, filledFields, movedLeads}`. Mesma empresa: `400`.
  Destino arquivado ou as duas com lead aberto: `409`. Auditado
  (`COMPANY_MERGE`).
- **Excluir:** corpo `{"confirmationName": "..."}`, o nome da empresa (sem
  caixa nem espaço nas pontas), senão `400`. Com atividade comercial: `409`.
  Sem permissão (não cadastrou, ou passou o prazo, e não é administrador):
  `403`. Sucesso: `204`; a empresa, o lead aberto com ela e a proveniência
  são apagados, e a auditoria (`COMPANY_DELETE`) guarda só o id.
  `/removal` responde `{allowed, blocker: HAS_ACTIVITY|NOT_ALLOWED,
  allowedUntil}`.

## 5. Leads

`/leads/worklist` aceita `search`, `status`, `priority`, `city`, `segment`
(sem distinguir caixa), `noSegment`, `source` (origem da empresa, D29),
`assignee`, `unassigned`, `scoreMin`, `scoreMax`, `focus` (`OVERDUE`,
`STALE`, `WITHOUT_TASK`), `sort` (`COMPANY`, `SCORE`, `CREATED`, `UPDATED`,
`STAGE`, `VALUE`, `NEXT_TASK`), `direction` (`asc`, `desc`), `page` e `size`
(até 100). Nulos sempre por último na ordenação.

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/leads` | Criar lead |
| GET | `/leads` | Listar e filtrar |
| GET | `/leads/worklist` | Fila de trabalho: lista com responsável, valor, próxima tarefa e tempo na etapa (D31) |
| GET | `/leads/{leadId}/summary` | Os mesmos campos de uma linha da fila, para um lead |
| POST | `/leads/bulk` | Responsável ou prioridade de até 100 leads, tudo ou nada (D31) |
| GET | `/leads/views` | Visões próprias e as compartilhadas com a equipe (`shared`, `canDelete`) |
| POST | `/leads/views` | Salvar visão (`name`, `query`, `shared`; compartilhar só administrador) |
| DELETE | `/leads/views/{viewId}` | Apagar visão própria; compartilhada só administrador |
| GET | `/companies/segments` | Segmentos em uso, com quantas empresas, e quantas estão sem segmento |
| GET | `/leads/{leadId}` | Detalhar |
| PATCH | `/leads/{leadId}` | Atualizar dados permitidos |
| PATCH | `/leads/{leadId}/status` | Alterar status |
| PATCH | `/leads/{leadId}/priority` | Alterar prioridade |
| GET | `/leads/{leadId}/timeline` | Histórico consolidado |

Filtros sugeridos: `status`, `priority`, `segment`, `city`, `scoreMin`, `scoreMax`, `source`, `search`.

Contrato implementado na Etapa 4:

- **Criação:** `{"companyId": "...", "priority": "HIGH", "source": {...}}`.
  `priority` ausente vira `MEDIUM`; `source` segue a mesma regra de empresas.
  O lead nasce `NEW`, com o primeiro evento no histórico. Empresa de outra
  organização responde `404`; arquivada, ou com oportunidade aberta, `409`.
- **Status:** `{"status": "QUALIFIED", "reason": "...", "contactConfirmed": false}`.
  Transição fora de `LeadStatusTransitions` responde `409`. Ir para
  `CONTACTED` exige `contactConfirmed: true` (doc 06 §2); sem isso, `400`.
  Reenviar o status atual não gera evento. `WON`/`LOST` preenchem `closedAt`.
- **PATCH `/leads/{id}`:** `{"assignedUserId": "..."}`; `null` remove o
  responsável. Usuário que não é membro ativo da organização responde `404`.
- **Resposta:** inclui `company` resumida e `allowedTransitions`, que orienta a
  interface; a regra continua no servidor. `score` ausente significa sem
  análise, nunca zero (D7).
- **Timeline:** eventos `STATUS_CHANGE` e `SOURCE`, do mais recente ao mais
  antigo, paginados numa única ordem.
- **Listagem:** `scoreMin`/`scoreMax` não incluem lead sem score;
  `scoreMin > scoreMax` responde `400`. Ordenação por `createdAt`, `updatedAt`,
  `score` ou `companyName`.

## 6. Importação

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/imports/csv` | Enviar CSV e analisar (não cria empresa) |
| GET | `/imports` | Listar importações, paginado; filtro `status` (`ANALYZED`, `COMPLETED`, `DISCARDED`) |
| GET | `/imports/summary` | Visão geral: `pendingReview`, `completed`, `undone`, `discarded`, `companiesImported` |
| GET | `/imports/{importId}` | Consultar status e totais |
| GET | `/imports/{importId}/report` | Resultado por linha, paginado, filtro `status` |
| POST | `/imports/{importId}/confirm` | Importar as linhas aceitas |
| POST | `/imports/{importId}/discard` | Desistir sem importar |
| GET | `/imports/{importId}/undo` | Prévia de desfazer: o que sai e o que fica |
| POST | `/imports/{importId}/undo` | Desfazer importação concluída (D32) |

Para arquivos grandes, usar processamento assíncrono. Validar tamanho, encoding, colunas e conteúdo. Não executar fórmulas de planilha; proteger exportações contra CSV injection.

Contrato implementado na Etapa 5 (D14, D23):

- **Envio:** `multipart/form-data` com `file` (`.csv` ou `.txt`, UTF-8, até
  2 MB e 2.000 linhas), `sourceName` e `datasetNote` opcionais e `mapping`
  opcional, um JSON `{"name": "Coluna", "city": "Coluna", ...}` com as chaves
  do cadastro de empresa. Sem `mapping`, os cabeçalhos são reconhecidos por
  apelido (`Razão Social`, `Município`, `UF`, `Telefone`…). `name`, `city` e
  `state` precisam de coluna; sem elas, `400`.
- **Resposta da análise:** `201` com status `ANALYZED`, totais
  (`totalRows = validRows + rejectedRows + duplicateRows`), `columnMapping`,
  `ignoredColumns` e `previousImportId` quando o mesmo arquivo já foi enviado.
- **Linha do relatório:** `rowNumber` (cabeçalho é a linha 1), `status`
  (`VALID`, `REJECTED`, `DUPLICATE`, `IMPORTED`, `MERGED`, `SKIPPED`, e
  `REMOVED` quando a empresa foi excluída depois, D32), `values`,
  `errors` no mesmo formato de erro de campo da API, e para duplicatas
  `duplicateOfCompanyId` ou `duplicateOfRowNumber` com `duplicateSignals`.
- **Confirmação:** `{"includeDuplicateRows": [7], "mergeDuplicateRows": [6],
  "createLeads": true}`. `includeDuplicateRows` importa a duplicata como
  empresa nova; `mergeDuplicateRows` completa os campos vazios da empresa
  existente (só para duplicata de empresa cadastrada), sem sobrescrever nada.
  Linha que não é possível duplicata, `merge` de duplicata de outra linha do
  arquivo ou a mesma linha nas duas listas respondem `400`; confirmar ou
  descartar importação que não está `ANALYZED` responde `409`.
- `mergedRows` conta as linhas que completaram empresa; a linha fica `MERGED`
  com `companyId` da empresa existente e `mergedFields` com os campos
  preenchidos.
- **Desfazer** (D32): só `COMPLETED` e uma vez (`409` caso contrário). Exclui
  as empresas que a importação criou e que continuam sem atividade; as com
  atividade ficam (`keptWithActivity`), as que ela só completou não mudam
  (`mergedUntouched`). Quem importou, até 7 dias depois da conclusão, ou o
  administrador; senão `403`. A importação passa a ter `undoneAt`. Auditado
  (`IMPORT_UNDO`).
- Importação de outra organização responde `404` em todas as rotas.

## 7. Discovery

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/discovery/searches` | Criar pesquisa |
| GET | `/discovery/searches` | Listar pesquisas |
| GET | `/discovery/searches/{searchId}` | Consultar resultado |
| POST | `/discovery/searches/{searchId}/import` | Importar resultados selecionados |

Exemplo de critérios:

```json
{
  "city": "Poços de Caldas",
  "state": "MG",
  "segment": "BARBERSHOP",
  "limit": 20,
  "source": "MANUAL"
}
```

A API deve rejeitar fontes não configuradas ou não autorizadas. O limite é um limite de interface/consulta, não autorização para coleta ilimitada.

## 8. Análises

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/leads/{leadId}/analyses` | Solicitar análise |
| GET | `/leads/{leadId}/analyses` | Listar análises |
| GET | `/analyses/{analysisId}` | Detalhar análise |
| PATCH | `/findings/{findingId}/review` | Revisar achado |

Solicitações demoradas devem retornar `202 Accepted` com identificador de job ou análise.

Contrato implementado na Etapa 6 (D24):

- **Pedido:** `POST /leads/{leadId}/analyses` responde `202` com a análise
  `PENDING`. Com análise `PENDING` ou `PROCESSING`, devolve a mesma. Lead
  `DO_NOT_CONTACT` responde `409`; lead de outra organização, `404`.
- **Status:** `PENDING`, `PROCESSING`, `COMPLETED` ou `FAILED`, com
  `createdAt`, `startedAt`, `completedAt` e `failureReason` resumido.
  `score` e `scoreVersion` só existem com a análise concluída.
- **Detalhe:** `GET /analyses/{id}` devolve `analysis` e `findings`, um por
  critério, com `status`, `originalStatus`, `description`, `confidence`,
  `pointsAwarded`, `maxPoints`, revisão e `evidence` (tipo, URL de
  referência, descrição, data, autor e hash do conteúdo).
- **Revisão:** `PATCH /findings/{id}/review` com
  `{"status": "CONFIRMED", "note": "..."}`. Responde o detalhe recalculado.
  Análise não concluída responde `409`; achado de outra organização, `404`.

## 9. CRM

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/pipeline-stages` | Listar etapas (D25: no código, 1:1 com `Lead.status`) |
| GET | `/pipeline/board` | Quadro Kanban: até 50 cards e o total por etapa |
| GET | `/leads/{leadId}/deal` | Consultar deal (404 antes da proposta) |
| PUT | `/leads/{leadId}/deal` | Atualizar serviço, valor e motivo de perda |
| POST | `/leads/{leadId}/interactions` | Registrar interação |
| GET | `/leads/{leadId}/interactions` | Listar interações |
| POST | `/tasks` | Criar tarefa |
| PATCH | `/tasks/{taskId}` | Atualizar tarefa (estado completo) |
| GET | `/leads/{leadId}/tasks` | Listar tarefas, abertas primeiro |

Contrato implementado na Etapa 7 (D2, D25):

- Mover card é `PATCH /leads/{leadId}/status`. Não existem `/pipelines`,
  `POST /deals` nem `PATCH /deals/{id}/stage`: o deal nasce sozinho na
  proposta, e a etapa é o status do lead.
- **Deal:** `{"service": "...", "estimatedValue": 1500.50, "lossReason": "..."}`,
  valor com no máximo duas casas e não negativo. `outcome` (`OPEN`, `WON`,
  `LOST`) vem do status do lead. Antes da proposta, `PUT` responde `409`.
- **Interação:** `type` (`CONTACT`, `REPLY`, `MEETING`, `PROPOSAL`,
  `DO_NOT_CONTACT_REQUEST`, `NOTE`), `channel`, `direction` (`OUTBOUND`,
  `INBOUND`, `INTERNAL`), `occurredAt` (não no futuro), `outcome`, `notes`.
  Abordagem de saída a lead `DO_NOT_CONTACT` responde `409`.
- **Tarefa:** `{"leadId", "title", "dueAt", "assignedUserId"}`; o responsável
  precisa ser membro ativo (outro, `404`). O `PATCH` recebe o estado completo
  com `status` `OPEN` ou `DONE`.
- **Quadro** (`/pipeline/board`, filtros `search`, `segment`, `city`): cada
  coluna traz `total` e `totalValue` (soma do valor dos negócios da etapa
  inteira, não só dos cards mostrados, em BRL). `Ganho` e `Perdido` trazem só
  os encerrados nos últimos 30 dias e `recentDays: 30`; os mais antigos seguem
  em `/leads`. Cada card traz `estimatedValue` e `stageEnteredAt`, o instante
  da última mudança de status pela ordem de gravação, base do "dias na etapa".

## 10. Outreach

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/leads/{leadId}/outreach-context` | Remetente, contexto da IA e destinatário/bloqueio por canal |
| POST | `/leads/{leadId}/messages/generate` | Gerar rascunho |
| GET | `/leads/{leadId}/messages` | Listar mensagens |
| PATCH | `/messages/{messageId}` | Editar rascunho |
| POST | `/messages/{messageId}/approve` | Aprovar |
| POST | `/messages/{messageId}/record-send` | Registrar envio confirmado |
| POST | `/messages/{messageId}/cancel` | Cancelar |
| POST | `/suppression-entries` | Registrar não contato |
| GET | `/suppression-entries` | Consultar bloqueios autorizados |

A ação `record-send` registra envio já realizado; não deve abrir uma integração de disparo oculto. Integrações oficiais futuras devem possuir endpoints e controles próprios.

Contratos da Etapa 8 (D26):

- `generate` recebe `{channel}` (`WHATSAPP`, `EMAIL`, `INSTAGRAM`, `PHONE_CALL`) e responde `201` com a mensagem em `DRAFT` ou `NEEDS_REVIEW`. `409` quando há bloqueio ou a empresa não tem contato válido no canal; `429` acima de 10 gerações por usuário em 5 minutos; `502 AI_PROVIDER_ERROR` quando o provedor falha ou a saída é recusada, sem gravar rascunho.
- `PATCH /messages/{id}` recebe `{subject?, body}`; assunto só em e-mail; limite por canal (1.000, 4.000, 1.000 e 2.000 caracteres).
- `approve` recebe `{unverifiedClaimsReviewed}`, obrigatório como `true` em `NEEDS_REVIEW`.
- `record-send` recebe `{result: SENT|FAILED, sentAt, notes?, markLeadContacted?}`; só de `APPROVED`. `SENT` cria interação `CONTACT` na timeline; o lead só vai a `CONTACTED` com `markLeadContacted: true`.
- `POST /suppression-entries` recebe `{identifierType: PHONE|EMAIL|INSTAGRAM, identifier, channel?, reason, requestedAt?}`; qualquer membro; idempotente por identificador e canal. `GET` só para `ADMIN`. Não há remoção.

## 11. Website Studio

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/website-templates` | Listar templates |
| GET | `/website-templates/{templateId}` | Detalhar template |
| POST | `/website-projects` | Criar projeto/prévia |
| GET | `/website-projects/{projectId}` | Consultar projeto |
| PATCH | `/website-projects/{projectId}` | Atualizar configuração |
| POST | `/website-projects/{projectId}/preview` | Gerar prévia |

## 12. Segurança dos endpoints

- Autenticação em todas as rotas privadas.
- Autorização por papel e organização.
- Validar ownership em cada recurso.
- Rate limit em login, pesquisa e geração de IA.
- Limites de payload e upload.
- Timeouts nas integrações.
- Não retornar stack trace.
- Não registrar tokens, senhas ou conteúdo pessoal desnecessário.
- OpenAPI deve refletir os contratos reais implementados.

## 13. Contratos e evolução

Os exemplos deste documento são contratos iniciais. Antes de implementar cada módulo:
1. Definir DTOs completos.
2. Definir enums e erros.
3. Definir regras de autorização.
4. Atualizar OpenAPI.
5. Escrever testes de contrato/integrados.
6. Atualizar este documento se houver mudança relevante.
