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
| D4 | `Company.normalized_name` | Aprovada | 4 |
| D5 | `CompanyContact` fora do P0 | Aprovada | 4 |
| D6 | `LeadSource.company_id` obrigatório | Aprovada | 4 |
| D7 | Score sem análise (baseline) | Proposta | 6 |
| D8 | Ownership em `/findings/{id}/review` | Proposta | 6 |
| D9 | Pipeline semeada, sem CRUD | Proposta | 7 |
| D10 | Normalização compartilhada | Aprovada | 4 |
| D11 | `WebsiteTemplate` é entidade global | Proposta | pós-MVP |
| D12 | Verificação de dado de `Company` | Aprovada | 4 |
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
| D23 | Importacao CSV em duas fases | Aprovada | 5 |
| D24 | Criterios do score v1 e verificacao de site | Proposta | 6 |
| D25 | Pipeline no codigo, deal sem resultado, interacoes | Proposta | 7 |

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

## D4 — `normalized_name` é coluna persistida · Aprovada (Etapa 4, 2026-11-05)

`docs/04` §2 cita `(organization_id, normalized_name, city, state)` como índice
sugerido, mas `normalized_name` não consta na lista de campos de `Company`.

**Proposta:** persistir a coluna, preenchida pela aplicação via o componente de
D10. Calcular em memória impediria o índice, e a deduplicação depende dele.
O nome original é sempre preservado, conforme `docs/04` §2.

## D5 — `CompanyContact` fora do P0 · Aprovada (Etapa 4, 2026-11-05)

Telefone e e-mail existem em `Company` **e** em `CompanyContact`, tornando
ambígua a deduplicação por telefone (`docs/06` §4) e a checagem de bloqueio.

**Proposta:** não implementar `CompanyContact` no MVP. Os campos de `Company`
bastam para o marco de cerca de 30 empresas. Reintroduzir quando houver
necessidade real de múltiplos contatos por empresa, momento em que `Company`
mantém o contato principal (exibição e dedup) e `CompanyContact` passa a ser a
lista completa.

## D6 — `LeadSource.company_id` obrigatório · Aprovada (Etapa 4, 2026-11-05)

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

## D10 — Componente único de normalização · Aprovada (Etapa 4, 2026-11-05)

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

## D12 — Verificação de dado vive na análise · Aprovada (Etapa 4, 2026-11-05)

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

**Agrupamento hierarquico (revisado em 2026-11-05).** A versao anterior
agrupava o IPv6 apenas por `/48`. Isso impedia a rotacao, mas fazia um
assinante abusivo travar todos os vizinhos de um provedor que distribui `/64`
a partir do mesmo `/48`. Agora sao tres limites, do mais amplo ao mais
estreito:

| Nivel | Chave | Cota na janela |
|---|---|---|
| Site | `/48` IPv6 ou IPv4 exato | 100 (`MAX_ATTEMPTS_PER_SITE`) |
| Assinante | `/64` IPv6 ou IPv4 exato | 50 (`MAX_ATTEMPTS_PER_NETWORK`) |
| Credencial | e-mail + assinante | 5 (`MAX_FAILURES`) |

Quando o assinante recusa, a cobrança no site e **estornada**: sem o estorno,
um `/64` ja bloqueado que continua insistindo drenaria a cota compartilhada e
travaria os vizinhos de qualquer forma. Trocar de `/64` continua custando ao
site, porque cada `/64` novo e aceito no nivel do assinante. Recusa no nivel
da credencial nao e estornada: a tentativa aconteceu e conta para o volume da
origem.

**Saturacao encarecida, nao tolerada.** Ao atingir o teto, tentativas de
chaves ainda nao vistas sao recusadas. Permiti-las seria bypass: o limite
deixaria de valer a partir da saturacao. Recusar tem custo de
disponibilidade, e o que torna esse custo aceitavel e a saturacao ser
inalcancavel para uma origem unica:

- quem controla um `/48` inteiro obtem 100 tentativas na janela, o dobro de
  uma origem comum, e nao 65.536 vezes isso;
- cada tentativa cria no maximo uma entrada em cada mapa, entao os mapas de
  assinantes e de credenciais tem teto `MAX_TRACKED_SITES x
  MAX_ATTEMPTS_PER_SITE` (100.000) e nunca saturam antes do mapa de sites;
- chegar a recusa exige, portanto, mil sites distintos (`/48` ou IPv4), de
  operadores diferentes.

Consequencia para os testes: so o mapa de sites alcanca o teto por inundacao,
e e nele que o teste de "descarte nao remove bloqueio ativo" e exercitado. O
descarte e o mesmo codigo nos tres mapas.

A saturacao e registrada em log de erro, uma vez por janela: sem isso, o
sintoma observado seria apenas "ninguem consegue entrar", sem causa aparente.

**Limite deste desenho.** Sob ataque realmente distribuido, limitador em
memoria nao e a defesa adequada; o caminho e filtragem na borda. Esta decisao
escolhe deliberadamente proteger a senha em vez da disponibilidade nesse
cenario extremo, e registra a escolha em vez de esconde-la.

**Historico desta decisao.** Quatro revisoes de seguranca apontaram problemas
em sequencia nesta classe: condicao de corrida, memoria sem limite, descarte de
bloqueio ativo, contorno por rotacao de IPv6 e, por fim, a oscilacao entre
recusar e permitir na saturacao. A oscilacao se resolveu ao perceber que a
escolha entre falhar fechado e falhar aberto so era dolorosa porque saturar era
barato; encarecer a saturacao dissolve o dilema.

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

## D23 — Importacao CSV em duas fases · Aprovada (Etapa 5, 2026-11-05)

`docs/04` nao define entidade de importacao, e `docs/05` §6 lista apenas envio,
consulta e relatorio. O roadmap pede, porem, "revisar e persistir registros
validos", e o RF-014 proibe descartar registro em silencio.

**Proposta:** importar em duas fases, com duas tabelas novas (V5).

1. `POST /imports/csv` **analisa**: le o arquivo, reconhece os cabecalhos
   (ou aplica o mapeamento enviado), valida cada linha com as mesmas regras do
   cadastro manual e classifica duplicatas contra a base e dentro do proprio
   arquivo. Grava `import_batch` e uma `import_row` por registro. **Nao cria
   empresa.**
2. `POST /imports/{id}/confirm` **importa** as linhas validas e as duplicatas
   que o operador escolheu, opcionalmente abrindo um lead por empresa. Antes de
   gravar, reclassifica as validas contra a base: empresa cadastrada entre a
   analise e a confirmacao vira linha pulada, visivel no relatorio, e nao
   duplicata silenciosa.
3. `POST /imports/{id}/discard` desiste e apaga os valores das linhas
   (minimizacao, doc 07 §2), mantendo os totais.

**Regras:**

- Limites da D14: 2 MB, 2.000 linhas, 50 colunas. Arquivo acima do limite e
  recusado inteiro; nunca importado pela metade.
- UTF-8 obrigatorio. Decodificar no charset errado nao falha: troca acentos por
  lixo em silencio. Delimitador `;`, `,` ou tabulacao, detectado pelo cabecalho.
- Colunas que nao correspondem a campo da empresa sao ignoradas e listadas na
  resposta; nao sao guardadas. A linha bruta do arquivo tambem nao e guardada.
- Confirmacao numa unica transacao, com transicao de status atomica
  (`ANALYZED` para `IMPORTING`): duas confirmacoes simultaneas nao importam em
  dobro, e uma falha desfaz tudo.
- Proveniencia por empresa: origem `CSV_IMPORT`, nome informado pelo operador
  (ou "Importacao CSV: arquivo"), referencia "Importacao {id}, linha N" e data
  da analise como data de coleta.
- Reenvio do mesmo arquivo e identificado pelo SHA-256 do conteudo e avisado
  (`previousImportId`); as linhas ja importadas aparecem como duplicatas.
- Importacao so cria registros novos, nunca altera existentes: nao tem como
  remover bloqueio de contato (doc 10 §6).

**Completar empresa existente (revisao de 2026-11-05, V7).** Uma linha
marcada como duplicata de empresa ja cadastrada pode, por escolha do operador,
completar essa empresa em vez de criar outra:

- so campos vazios sao preenchidos; nenhum valor existente e sobrescrito
  (RF-023). Nome, cidade e estado nunca mudam, porque identificam a empresa;
- a origem `CSV_IMPORT` e registrada na empresa mesmo sem campo preenchido,
  porque a linha tambem confirma a empresa (RF-022);
- o relatorio mostra quais campos foram preenchidos (`MERGED`, `mergedFields`);
- empresa arquivada depois da analise nao e alterada: a linha fica `SKIPPED`;
- com abertura de leads, a empresa so ganha lead se nao tiver oportunidade
  aberta. Oportunidade aberta, inclusive `DO_NOT_CONTACT`, nao e duplicada e
  nao derruba a importacao;
- duplicata de outra linha do mesmo arquivo nao pode completar: nao ha empresa
  cadastrada a completar.

**Fora do escopo:** fundir duas empresas ja cadastradas (mover leads, origens
e historico de uma para outra). Exige revisao propria (doc 06 §4: preservar
fontes e historico, permitir auditar) e fica para quando houver necessidade
real.

## D24 — Criterios do score v1 e verificacao de site · Proposta (Etapa 6)

O doc 06 §7 define grupos de pontos ("problema tecnico verificavel, ate 20"),
mas nao como cada ponto e obtido; e a analise automatica precisa de regras
que nunca afirmem sem evidencia.

**Criterios.** Cada criterio e uma afirmacao e vale seus pontos inteiros
apenas quando `CONFIRMED`; qualquer outro estado vale zero. Os grupos fecham
exatamente a tabela do doc 06 §7:

| Criterio | Pontos | Verificacao automatica |
|---|---:|---|
| `SITE_ABSENT` — a empresa nao tem site | 25 | Nunca confirma. Sem site cadastrado ou site fora do ar: `NOT_VERIFIED`. So o operador confirma ausencia. |
| `NO_HTTPS` — o site nao responde por HTTPS | 10 | Conexao HTTPS ao host. |
| `NO_MOBILE_VIEWPORT` — pagina inicial sem meta viewport | 10 | HTML da pagina inicial; confianca media. |
| `NO_CONTACT_LINK` — pagina inicial sem link `tel:`, `mailto:` ou WhatsApp | 10 | HTML da pagina inicial; confianca media. |
| `NO_ONLINE_BOOKING_OR_CATALOG` | 10 | Nenhuma. Sempre `NOT_VERIFIED`; heuristica por texto seria inferir sem evidencia. |
| `BUSINESS_INFO_SUFFICIENT` — cadastro com segmento e canal de contato | 15 | Cadastro da empresa. |
| `SERVICE_FIT` — segmento entre os atendidos | 10 | `jhcomp.analysis.served-segments`; vazio deixa para revisao. |
| `LOCATION_SERVED` — estado entre os atendidos | 10 | `jhcomp.analysis.served-states`, padrao `MG`. |

**Score.** `ScoreFormula` v1 e funcao pura dos estados. A analise grava score e
versao; o lead recebe o score da ultima analise concluida (resolve D3). Os
criterios independentes de site (D7) sao calculados dentro de cada analise,
o que equivale a "baseline somado" e mantem uma unica fonte. Lead sem analise
nao tem score, e a interface mostra "sem analise", nunca zero.

**Revisao.** O operador escolhe o estado do achado; o original da verificacao
fica gravado (`original_status`), a nota vira evidencia `OPERATOR_NOTE` e o
score e recalculado. A posse da rota `/findings/{id}/review` e verificada
pela organizacao do achado; as chaves compostas garantem a cadeia achado,
analise, lead e organizacao (resolve D8).

**Execucao.** Pedido responde `202` e enfileira um job na mesma transacao
(D15): tabela `job`, worker `@Scheduled` com `FOR UPDATE SKIP LOCKED`, tres
tentativas com espera crescente, job travado ha mais de 10 minutos volta a
fila. Uma analise ativa por lead; pedir de novo devolve a mesma. Lead
`DO_NOT_CONTACT` nao e analisado: a analise prepara abordagem, e processar
dados de quem pediu para nao ser contatado nao tem finalidade.

**Verificacao do site (docs/07 §6, regra 8 do produto).**

- So `http`/`https` nas portas 80 e 443; sem cookies, autenticacao ou proxy.
- O endereco e validado no `DnsResolver` usado na propria conexao (Apache
  HttpClient 5), o que fecha DNS rebinding. Bloqueia loopback, redes
  privadas, link-local (inclui metadata 169.254.169.254), CGNAT, IPv6 local,
  faixas reservadas e IPv4 embutido em IPv6.
- Sem redirecionamento automatico; ate 3, cada um revalidado, e o robots.txt
  do novo host respeitado.
- Timeout de conexao e leitura, e um prazo total de 15 s por requisicao
  imposto por temporizador que cancela a requisicao, cobrindo cabecalhos
  enviados um byte por vez. Prazo de 45 s para a verificacao inteira.
  Cabecalho limitado a 100 linhas de 8 KB; corpo, a 1 MB. A requisicao e
  cancelada antes de fechar: fechar sem cancelar drena o restante do corpo, e
  um servidor que pinga bytes devagar prenderia o worker.
- IPv6 por lista de permissao: so unicast global (2000::/3), sem 6to4, Teredo,
  ORCHID e documentacao; formas que carregam IPv4 sao recusadas.
- robots.txt respeitado (RFC 9309): 4xx libera, 5xx ou sem resposta proibe.
  Curinga comparado sem expressao regular (regras de terceiros com varios
  `*` causariam ReDoS), com teto de 500 regras de 512 caracteres.
- Da pagina saem apenas dois sinais booleanos e o SHA-256 do conteudo. O HTML
  nao e guardado nem interpretado como instrucao (regra 9 do produto).

**Fora do escopo:** IA (Etapa 8), navegacao alem da pagina inicial e
deteccao automatica de agendamento ou catalogo.

## D25 — Pipeline no codigo, deal sem resultado, interacoes · Proposta (Etapa 7)

Tres pontos que os docs deixam abertos ou que a D2 e a D9 nao fecham.

**Etapas da pipeline no codigo, sem tabela.** A D9 propunha tabelas
`pipeline` e `pipeline_stage` semeadas por Flyway. Como as etapas sao 1:1 com
`Lead.status` (D2) e nao tem CRUD no MVP, uma tabela seria segunda fonte de
verdade a manter sincronizada com o enum, e organizacao criada depois da
migration ficaria sem pipeline. As etapas vivem em `PipelineStages`; `GET
/pipeline-stages` as expoe. `DO_NOT_CONTACT` nao e etapa: e bloqueio, fora do
fluxo comercial. Quando a pipeline precisar ser configuravel, ganha tabela.
Substitui a parte de tabelas da D9; o "sem CRUD" continua.

**Deal sem resultado.** O deal guarda servico, valor (`numeric(12,2)`, BRL) e
motivo de perda. Ganho ou perdido e o status do lead: guardar o resultado no
deal recriaria duas fontes de estado, o que a D2 eliminou. A resposta da API
traz `outcome` derivado do lead. O deal nasce ao chegar a `PROPOSAL_SENT` ou a
`WON` sem proposta, por evento publicado na mesma transacao da mudanca de
status; ao chegar a `LOST`, o motivo informado vira motivo de perda se o deal
ainda nao tiver um.

**Interacoes.** Registrar interacao nunca muda o status do lead: contato
registrado nao vira `CONTACTED` sozinho (regra 6). Excecao: pedido de nao
contato, que precisa valer na hora (doc 06 §9), move o lead para
`DO_NOT_CONTACT` se o estado permitir. Abordagem de saida (contato, reuniao,
proposta) a lead bloqueado e recusada (regra 4); o que a empresa fizer por
iniciativa propria continua registravel. Interacoes entram na timeline do lead.
A lista de nao contato propriamente dita (`SuppressionEntry`, por identificador
e canal) e da Etapa 8.

**Dashboard.** Periodo em datas de Brasilia (padrao: ultimos 30 dias), com
instantes em UTC. Contagem por etapa e uma foto atual; empresas adicionadas,
contatos registrados e negocios ganhos respeitam o periodo, que volta na
resposta para o denominador ficar explicito.
