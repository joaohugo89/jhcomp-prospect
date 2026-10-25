# 11 — Registro de decisões

Este documento resolve lacunas e inconsistências encontradas na auditoria da
especificação (docs 01–10). Ele **complementa** os demais documentos; onde
houver divergência, prevalece o que está aqui, e o documento de origem deve ser
corrigido quando a etapa correspondente for implementada.

Status: `Aprovada` (decidida pelo responsável) · `Proposta` (a confirmar na etapa indicada).

| ID | Assunto | Status | Etapa |
|---|---|---|---|
| D1 | Estratégia de autenticação | Aprovada | 3 |
| D2 | Estado do lead vs. etapa do deal | Aprovada | 7 |
| D3 | Onde o score é calculado e persistido | Proposta | 6 |
| D4 | `Company.normalized_name` | Proposta | 4 |
| D5 | `CompanyContact` fora do P0 | Proposta | 4 |
| D6 | `LeadSource.company_id` obrigatório | Proposta | 4 |
| D7 | Score sem análise (baseline) | Proposta | 6 |
| D8 | Ownership em `/findings/{id}/review` | Proposta | 6 |
| D9 | Pipeline semeada, sem CRUD | Proposta | 7 |
| D10 | Normalização compartilhada | Proposta | 4 |
| D11 | `WebsiteTemplate` é entidade global | Proposta | pós-MVP |
| D12 | Verificação de dado de `Company` | Proposta | 4 |
| D13 | Website Studio fora do MVP | Aprovada | pós-MVP |
| D14 | Importação CSV síncrona no MVP | Proposta | 5 |
| D15 | Tabela de jobs em vez de `@Async` | Proposta | 6 |
| D16 | Granularidade de módulos: 12 para 8 | Proposta | 3 |
| D17 | Escopo de testes E2E reduzido | Proposta | 9 |
| D18 | `audit_event.ip_address` como `text` | Aprovada | 3 |
| D19 | Semente de usuario por perfil, nao por migration | Aprovada | 3 |
| D20 | Limitador de login com reserva atomica e teto de memoria | Aprovada | 3 |
| D21 | Revalidacao da associacao a cada requisicao | Aprovada | 3 |
| D22 | Nomes de cookie proprios do projeto | Aprovada | 3 |

---

## D1 — Autenticação por sessão com cookie `HttpOnly` · Aprovada

`docs/05-api-specification.md` §2 deixava em aberto entre sessão e token.

**Decisão:** Spring Security com cookie de sessão `HttpOnly`, `Secure`,
`SameSite=Lax`, acompanhado de token CSRF. Frontend e backend servidos na mesma
origem por reverse proxy em produção.

**Motivo:** elimina rotação, revogação e blacklist de refresh token, complexidade
que não traz benefício com um único operador. Menos código e menor superfície de
erro. Aplicativo mobile está fora do MVP (`docs/01` §5), que é o caso em que JWT
compensaria.

**Consequências:** `/auth/refresh` sai da especificação da API. CSRF passa a ser
requisito de teste obrigatório. O frontend não guarda credencial em
`localStorage`, o que `docs/05` §2 já advertia.

## D2 — `Lead.status` é a fonte única de verdade · Aprovada

`docs/06-business-rules.md` §3 admitia manter `Lead.status` e `Deal.stage` como
estados independentes, exigindo apenas que a diferença fosse documentada.

**Decisão:** `Lead.status` é o único estado do ciclo comercial. As colunas do
Kanban são registros de `PipelineStage` cujo `stage_key` corresponde 1:1 a um
valor de `Lead.status`. Mover um card é `PATCH /leads/{leadId}/status`. `Deal`
passa a existir apenas para dados comerciais do fechamento (valor estimado,
serviço contratado, resultado e motivo de perda) e é criado quando o lead atinge
`PROPOSAL_SENT`.

**Motivo:** dois estados independentes divergem, e com um único operador nenhuma
rotina de conciliação percebe a divergência. Remove a classe inteira de bug de
sincronização.

**Consequências:** `PATCH /deals/{dealId}/stage` sai da especificação da API.
Múltiplas negociações simultâneas por empresa deixam de ser suportadas no MVP;
se necessárias, criar leads independentes, como `docs/04` já previa.

## D3 — Derivação única do score · Proposta (Etapa 6)

O score aparece em três lugares: `AnalysisFinding.points_awarded`,
`LeadAnalysis.score` e `Lead.score`.

**Proposta:** cadeia única e explícita.
`AnalysisFinding.points_awarded` soma para `LeadAnalysis.score`, persistido com
`score_version`; `Lead.score` é o score da última análise concluída somado aos
critérios independentes de análise (ver D7). Revisar um achado recalcula a cadeia
inteira de forma reproduzível.

## D4 — `normalized_name` é coluna persistida · Proposta (Etapa 4)

`docs/04` §2 cita `(organization_id, normalized_name, city, state)` como índice
sugerido, mas `normalized_name` não consta na lista de campos de `Company`.

**Proposta:** persistir a coluna, preenchida pela aplicação via o componente de
D10. Calcular em memória impediria o índice, e a deduplicação depende dele.
O nome original é sempre preservado, conforme `docs/04` §2.

## D5 — `CompanyContact` fora do P0 · Proposta (Etapa 4)

Telefone e e-mail existem em `Company` **e** em `CompanyContact`, tornando
ambígua a deduplicação por telefone (`docs/06` §4) e a checagem de bloqueio.

**Proposta:** não implementar `CompanyContact` no MVP. Os campos de `Company`
bastam para o marco de cerca de 30 empresas. Reintroduzir quando houver
necessidade real de múltiplos contatos por empresa, momento em que `Company`
mantém o contato principal (exibição e dedup) e `CompanyContact` passa a ser a
lista completa.

## D6 — `LeadSource.company_id` obrigatório · Proposta (Etapa 4)

`docs/04` §2 define `lead_id` e `company_id` ambos nullable, permitindo registro
de proveniência órfão.

**Proposta:** `company_id` obrigatório, pois a proveniência descreve a origem do
dado da empresa, e `lead_id` nullable. Garantir por constraint no banco.

## D7 — Score possui baseline sem análise · Proposta (Etapa 6)

Dois critérios da tabela de `docs/06` §7 não dependem de análise de presença
digital: "Compatibilidade com serviços oferecidos" (até 10) e "Localização
atendida" (até 10).

**Proposta:** explicitar que o score tem baseline de até 20 pontos sem nenhuma
análise. A interface não deve exibir "score 0" para lead não analisado como se
fosse avaliação negativa; deve indicar ausência de análise.

## D8 — Ownership por cadeia em rotas não escopadas · Proposta (Etapa 6)

`PATCH /findings/{findingId}/review` não carrega organização nem lead no path.

**Proposta:** validar a cadeia `finding` para `analysis` para `lead` para
`organization` contra a organização do contexto autenticado. Teste negativo de
acesso cruzado obrigatório, conforme `docs/10` §3.

## D9 — Pipeline semeada por migração, sem CRUD · Proposta (Etapa 7)

`Pipeline` e `PipelineStage` são configuráveis em `docs/04`, mas não há endpoint
de escrita em `docs/05` nem tela em `docs/08`.

**Proposta:** manter as tabelas e semear uma pipeline padrão via Flyway, com as
etapas de `docs/06` §3 alinhadas a `Lead.status` (ver D2). Sem CRUD e sem tela de
administração no MVP.

## D10 — Componente único de normalização · Proposta (Etapa 4)

A normalização de telefone aparece como regra da deduplicação (`docs/06` §4) e,
separadamente, como `contact_identifier_normalized` da lista de não contato
(`docs/04`, `docs/07` §10).

**Proposta:** um único componente em `shared`, consumido pelas duas rotinas,
cobrindo telefone, domínio, URL e nome.

**Motivo:** se as duas normalizações divergirem, um pedido de não contato pode
ser burlado por diferença de formatação do telefone. Isso é falha de
conformidade, não defeito cosmético. Cobrir com os casos de `docs/10` §4.

## D11 — `WebsiteTemplate` é global e não entra no filtro de tenant · Proposta

`WebsiteTemplate` não possui `organization_id`, ao contrário das demais
entidades. `docs/04` §2 já trata isso como intencional.

**Proposta:** marcar a entidade explicitamente como global no código, para que
não seja alcançada pelo filtro automático de organização nem confundida com dado
privado, conforme `docs/03` §8.

## D12 — Verificação de dado vive na análise · Proposta (Etapa 4)

RF-023 exige "não sobrescrever silenciosamente informação confirmada por outra
fonte", mas não existe status de verificação em `Company`: os estados de
verificação existem apenas em `AnalysisFinding`.

**Proposta:** não adicionar verificação por campo em `Company`. A verificação
permanece na análise e em `LeadSource`; a atualização de dados preserva histórico
de alterações relevantes, sem marcar campo a campo. Revisitar se o uso real
mostrar necessidade.

## D13 — Website Studio fora do MVP · Aprovada

`docs/09-mvp-roadmap.md` §9 colocava catálogo de templates e prévia de site no
Sprint 6. RF-060 e RF-061 já eram P1, não P0.

**Decisão:** adiar o módulo para depois do MVP. O antigo Sprint 6 passa a ser
endurecimento de produção, testes E2E e uso real com cerca de 30 empresas.

**Motivo:** para as primeiras empresas, montar uma demonstração à mão é mais
rápido do que construir catálogo, schema de configuração e motor de prévia, e a
JH.COMP já desenvolve sites como serviço. É também o módulo de maior risco
jurídico (`docs/06` §10: logotipos, fotografias, representação não autorizada) e
de menor validação, pois ainda não se sabe se a demonstração antecipada aumenta
conversão.

**Consequências:** RF-060, RF-061 e RF-062 saem do escopo do MVP. As tabelas
`WebsiteTemplate`, `WebsiteProject` e `WebsiteDeployment` não são criadas agora.

## D14 — Importação CSV síncrona no MVP · Proposta (Etapa 5)

`docs/05` §6 pede processamento assíncrono para arquivos grandes e RNF-004 exige
assincronia em operações demoradas.

**Proposta:** importação síncrona com limite rígido de 2.000 linhas e 2 MB,
retornando o relatório na própria resposta. `GET /imports/{importId}` e
`/report` continuam existindo e leem o registro persistido da importação, de modo
que o contrato da API não muda, apenas o modo de execução.

**Motivo:** o marco comercial é cerca de 30 empresas. Construir fila, polling e
tela de status para esse volume é complexidade sem retorno. Migrar para a tabela
de jobs (D15) depois é mudança local.

## D15 — Tabela de jobs persistente em vez de `@Async` · Proposta (Etapa 6)

`docs/03` §7 deixa a escolha em aberto.

**Proposta:** tabela de jobs com os campos já especificados em `docs/03` §7 e um
worker `@Scheduled` fazendo polling. Sem Redis, RabbitMQ ou Kafka, conforme o
mesmo documento determina.

**Motivo:** `@Async` perde trabalho em reinício. Uma análise que chama IA custa
dinheiro, e "custo médio de análise por lead" é métrica do produto (`docs/01`
§9), portanto perder a execução é perda financeira. Polling em tabela é mais
simples que qualquer broker e sobrevive a reinicialização.

## D16 — Granularidade de módulos: 12 para 8 · Proposta (Etapa 3)

`docs/03` §3 define 12 módulos e §4 define 4 camadas com cerca de 4 subpacotes
cada, resultando em aproximadamente 190 pacotes antes da primeira regra de
negócio. `docs/03` §4 já permite simplificar módulos pequenos.

**Proposta:** tornar a simplificação a regra.

- `enrichment` passa a integrar `company`, pois normalização e atualização são
  operações sobre Company.
- `organization` passa a integrar `identity`, pois `OrganizationMember` liga os
  dois e separá-los força contrato entre partes inseparáveis.
- As 4 camadas completas aplicam-se somente a `company`, `lead`, `analysis` e
  `outreach`, que têm regra de negócio real.
- `discovery` e `dashboard` usam estrutura plana. `dashboard` não tem `domain`,
  pois são consultas agregadas somente-leitura.

**Motivo:** o maior risco do projeto é um desenvolvedor sozinho afogado em
camadas. As fronteiras de dependência de `docs/03` §5 permanecem válidas.

## D17 — Escopo de testes E2E reduzido · Proposta (Etapa 9)

`docs/10` §2 lista 12 fluxos end-to-end.

**Proposta:** dois fluxos E2E. O primeiro cobre login, importar CSV, qualificar e
mover no pipeline. O segundo cobre gerar mensagem, registrar não contato e
confirmar que nova abordagem é bloqueada. Os outros dez passam a ser testes de
integração de API.

**Motivo:** suíte E2E grande mantida por uma pessoa fica frágil e passa a ser
ignorada. A cobertura de risco permanece equivalente, com custo de manutenção
muito menor.

## D18 — `audit_event.ip_address` e `text`, nao `inet` · Aprovada

A V2 definia a coluna como `inet`. O driver JDBC envia o parametro como
`varchar` e o PostgreSQL nao faz a conversao implicita, de modo que toda
insercao falhava com "column is of type inet but expression is of type
character varying".

**Decisao:** coluna `text` com `CHECK (length(ip_address) <= 45)`, que acomoda
IPv6. A validacao de formato que o tipo `inet` daria nao compensa o atrito de
mapeamento.

**Nota sobre imutabilidade:** a V2 foi corrigida no lugar, e nao por uma V3. A
regra de migration imutavel vale a partir do momento em que a migration roda em
algum ambiente compartilhado; esta so havia rodado no banco local e no de teste,
ambos descartaveis, e nada estava versionado. Daqui para frente, qualquer ajuste
na V1 ou na V2 exige uma nova versao.

## D19 — Usuario inicial criado por perfil, nao por migration · Aprovada

`docs/09` §3 pede "dados iniciais minimos para pipeline e papeis".

**Decisao:** organizacao e administrador iniciais sao criados por
`LocalBootstrap`, restrito ao perfil `local`, com a senha vinda de
`LOCAL_ADMIN_PASSWORD` e sem valor padrao. Sem a variavel, nada e criado.

**Motivo:** semear um usuario por migration Flyway colocaria uma senha conhecida
em todo ambiente que rodasse as migrations, producao incluida — e migrations sao
imutaveis, portanto o erro nao poderia ser simplesmente corrigido depois. Um
valor padrao para a senha seria uma credencial publicamente conhecida.

**Consequencia:** em producao, o primeiro administrador e criado por processo
operacional explicito, nao pelo deploy.

## D20 — Limitador de login com reserva atomica e memoria limitada · Aprovada

A primeira versao expunha `isBlocked()` e `recordFailure()` separados e nunca
removia entradas do mapa. Revisao de seguranca apontou dois problemas reais,
ambos alcancaveis pela rota publica `/auth/login`:

1. **Condicao de corrida (TOCTOU).** Entre a verificacao e a contabilizacao,
   requisicoes concorrentes passavam todas pelo teste antes de qualquer uma
   registrar falha. Com paralelismo, um atacante obtinha muito mais que as 5
   tentativas previstas.
2. **Memoria sem limite.** A chave inclui o e-mail, escolhido por quem chama.
   E-mails distintos criavam entradas que so eram removidas em login bem
   sucedido, permitindo exaustao de memoria.

**Decisao:** a unica operacao de entrada e `tryAcquire(email, ip)`, que verifica
o limite e contabiliza a tentativa no mesmo `compute` do `ConcurrentHashMap`,
atomico por chave. A tentativa e cobrada adiantado; `recordSuccess` devolve a
cota. O mapa tem teto de 20.000 chaves, com remocao das entradas expiradas e,
se necessario, descarte das mais antigas.

**Dois limites, nao um.** Limite por e-mail e IP (5 por janela) e limite por IP
(50 por janela). O limite por credencial sozinho nao basta: o e-mail compoe a
chave e e escolhido por quem chama, de modo que uma origem unica tentaria
e-mails sempre diferentes sem nunca ser barrada, e ainda encheria o
rastreamento. O limite por IP fecha essa saida.

**Teto de memoria sem abrir bypass.** Ao atingir o teto, removem-se as entradas
expiradas e, depois, as mais antigas **que nao estejam bloqueadas**. Nao ha
descarte de entrada bloqueada: seria bypass direto — bastaria inundar o
limitador para apagar o proprio bloqueio e recomecar, obtendo tentativas
ilimitadas. Sem candidato descartavel, novas chaves sao recusadas.

**Varredura espacada.** Acima do limiar, a limpeza de expirados ocorre no
maximo a cada 10 segundos. Varrer o mapa a cada tentativa custaria O(n) por
requisicao justamente sob ataque. O descarte por teto nao respeita o intervalo,
porque o limite de memoria precisa valer de imediato.

**Correcao de uma afirmacao anterior.** A primeira versao desta decisao dizia
que um teste garantia que inundar o limitador nao apagava bloqueio ativo. Nao
garantia: o teste inundava 10.100 chaves, enquanto o descarte so dispara em
20.000, portanto exercitava apenas a limpeza de expirados. Duas tentativas
seguintes de corrigir o teste tambem nao exercitavam o descarte — uma por
esgotar o teto de IPs antes do teto de chaves, outra por usar relogio
congelado, que torna "mais antiga" indefinido. A versao atual foi validada
removendo a protecao e confirmando que o teste falha.

**Agrupamento por rede.** O limite de volume e por rede de origem, nao por
endereco exato: em IPv6 um atacante costuma dispor de um bloco `/64` inteiro e
trocaria de endereco a cada tentativa, contornando o limite e enchendo o
rastreamento. IPv4 permanece exato, porque agrupar por `/24` atingiria
usuarios legitimos sem relacao com o atacante.

**Sem recusa por saturacao.** Ao atingir o teto, tentativas de chaves novas
passam sem ser rastreadas, em vez de serem recusadas. Recusar transformaria o
teto em bloqueio global de login: bastaria encher o mapa de entradas
bloqueadas para impedir qualquer operador de entrar. No estado saturado,
bloqueios ja registrados continuam valendo e o limite por rede ainda limita o
volume total.

**Limite conhecido:** o contador e em memoria e vale por instancia. Se o
backend passar a rodar replicado, precisa migrar para armazenamento
compartilhado.

## D21 — Revalidacao da associacao a cada requisicao · Aprovada

Revisao de seguranca apontou autorizacao obsoleta: `organizationId` e `role`
ficavam congelados na sessao por 8 horas. Revogar associacao, desabilitar
usuario, suspender organizacao ou rebaixar um administrador nao surtia efeito
algum ate a sessao expirar, o que contraria o critério de aceite do RF-003.

**Decisao:** `MembershipRevalidationFilter` recarrega a associacao ativa em cada
requisicao autenticada, antes da avaliacao de autorizacao. Associacao invalida
encerra a sessao; papel alterado substitui o principal e e regravado na sessao.

**Custo:** uma consulta indexada por requisicao autenticada, irrelevante no
volume do MVP. A alternativa, revalidar a cada N minutos, deixaria uma janela
em que o privilegio revogado continua funcionando. Se o volume crescer, o lugar
de introduzir intervalo ou cache e esse filtro.

## D22 — Nomes de cookie proprios do projeto · Aprovada

O login falhava no navegador com 403, enquanto todos os testes de API e as
verificacoes com `curl` passavam.

**Diagnostico:** cookie nao e isolado por porta. Todos os servicos em
`localhost` compartilham o mesmo conjunto de cookies. O navegador tinha um
`XSRF-TOKEN` residual, emitido antes da correcao de path da D18, e passou a
enviar dois cookies de mesmo nome. O servidor le apenas o primeiro, comparou o
cabecalho contra o valor errado e recusou a requisicao. A auditoria nao
registrava nada porque a recusa de CSRF ocorre no filtro, antes do controller.

**Decisao:** cookie `JH_XSRF` e cabecalho `X-JH-XSRF`, no lugar dos padroes
`XSRF-TOKEN` e `X-XSRF-TOKEN`; sessao em `JHSESSION`. O residuo antigo passa a
ser irrelevante, sem exigir limpeza manual no navegador de ninguem.

**Motivo de fundo:** esta maquina roda outros projetos em `localhost`
(5173, 8080, 55433). Nome generico de cookie colide entre eles, e o sintoma —
403 sem mensagem util — e caro de diagnosticar.

**Licao de processo:** mudanca de nome ou de path de cookie deixa orfao no
navegador, que continua sendo enviado. Testar com o residuo presente, e nao
apenas com pote de cookies limpo. Teste de API com `curl` nao reproduz as
regras de path e de duplicidade que o navegador aplica.
