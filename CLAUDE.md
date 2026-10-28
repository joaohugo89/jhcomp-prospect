# CLAUDE.md — JH.COMP Prospect

Ferramenta interna de prospecção comercial da JH.COMP (Poços de Caldas/MG).
Monólito modular Java 21 + Spring Boot, frontend React + TypeScript, PostgreSQL.

## Antes de implementar

A especificação está em `docs/`. Leia **apenas os documentos relevantes à etapa
atual**, não todos a cada sessão.

| Preciso de… | Leia |
|---|---|
| Requisito e critério de aceite | `docs/02-functional-requirements.md` |
| Camadas, módulos, adaptadores | `docs/03-architecture.md` |
| Entidades e índices | `docs/04-database-design.md` |
| Contrato de endpoint | `docs/05-api-specification.md` |
| Regra de negócio, score, dedup | `docs/06-business-rules.md` |
| Controle de segurança/LGPD | `docs/07-security-and-lgpd.md` |
| Tela, componente, estado de UI | `docs/08-ui-ux-specification.md` |
| Escopo da etapa | `docs/09-mvp-roadmap.md` |
| O que testar | `docs/10-testing-strategy.md` |
| **Decisões que sobrepõem os docs** | **`docs/11-decisions.md`** |

`docs/11-decisions.md` prevalece sobre os demais. Consulte-o antes de seguir
qualquer ponto que os docs 01–10 deixem em aberto — as ambiguidades conhecidas
já foram resolvidas lá (D1 a D17). Não reabra decisões marcadas `Aprovada`.

## Decisões em vigor (resumo; detalhe em `docs/11-decisions.md`)

- **D1** Autenticação por sessão: cookie `HttpOnly`/`Secure`/`SameSite=Lax` +
  CSRF. Sem JWT, sem refresh token. `/auth/refresh` não existe.
- **D2** `Lead.status` é a fonte única do estado comercial. Colunas do Kanban são
  `PipelineStage` com `stage_key` 1:1 para `Lead.status`. Mover card é
  `PATCH /leads/{leadId}/status`. `PATCH /deals/{dealId}/stage` não existe.
  `Deal` guarda só valor, serviço, resultado e motivo de perda.
- **D13** Website Studio está fora do MVP. Não criar `WebsiteTemplate`,
  `WebsiteProject` nem `WebsiteDeployment`.
- **D23** Importação CSV em duas fases: analisar não cria empresa; confirmar
  importa válidas, duplicatas escolhidas e completa empresas existentes só nos
  campos vazios. Limites da D14: 2 MB, 2.000 linhas.
- **D16** 8 módulos, não 12: `enrichment` dentro de `company`, `organization`
  dentro de `identity`. As 4 camadas completas só em `company`, `lead`,
  `analysis` e `outreach`; `discovery` e `dashboard` são planos.

## Comandos

### Ambiente completo em container (forma recomendada de testar)

```bash
cp .env.example .env                  # uma vez; defina LOCAL_ADMIN_PASSWORD
docker compose up -d --build          # postgres + backend + frontend
docker compose logs -f backend
docker compose ps                     # conferir health de cada serviço
docker compose down                   # para, preservando dados
docker compose down -v                # apaga o volume do banco
```

Aplicação em **http://localhost:8090**. O nginx do frontend faz proxy de
`/api/v1` para o backend, mantendo os dois na **mesma origem** — é o que a
decisão D1 exige para o cookie `SameSite=Lax` funcionar sem CORS com
credenciais. A porta 8081 fica publicada só para depuração com `curl`; em
produção não deve ser exposta.

Após mudar código do backend ou do frontend: `docker compose up -d --build`.

### Desenvolvimento sem container (iteração mais rápida)

```bash
docker compose up -d postgres         # só o banco

# Backend (requer POSTGRES_PASSWORD no ambiente para spring-boot:run)
cd backend  && mvn verify                 # compila + testes (sobe Postgres via Testcontainers)
cd backend  && mvn spring-boot:run        # API em :8081, base path /api/v1
cd frontend && npm run dev                # UI em :5174
cd frontend && npm run typecheck && npm run lint && npm test -- --run && npm run build
```

Conferência rápida: `curl http://localhost:8081/api/v1/health`.
OpenAPI em `/api/v1/openapi`, Swagger UI em `/api/v1/docs`.

Skills do projeto não são versionadas (só `skills-lock.json`, com hash de cada
uma). Em um clone novo: `npx skills install`. Instaladas:
`spring-boot-rest-api-standards`, `spring-boot-test-patterns`, `react-patterns`.

## Armadilhas desta stack já encontradas

Cada uma custou tempo real. Não reintroduzir.

- **Spring Boot 4 usa Jackson 3** (pacote `tools.jackson`). Propriedades do
  Jackson 2 como `spring.jackson.serialization.write-dates-as-timestamps`
  **não existem mais** e fazem o contexto falhar ao subir. Datas já saem como
  ISO-8601 por padrão.
- **Flyway exige `spring-boot-starter-flyway`** no Spring Boot 4. Declarar só
  `flyway-core`, como no Boot 3, coloca a biblioteca no classpath sem ativar a
  autoconfiguração: a aplicação sobe e **nenhuma migration é aplicada, em
  silêncio**. Sempre confirmar a linha `Successfully applied N migration`.
- **Testcontainers 2.x prefixa os artefatos** com `testcontainers-`
  (`testcontainers-postgresql`, não `postgresql`) e o BOM do Spring Boot 4 não
  gerencia mais suas versões: importar o `testcontainers-bom`.
- **Container de teste deve ser singleton**, iniciado em bloco estático sem
  `@Testcontainers`/`@Container`. Com essa extensão o container para ao fim de
  cada classe, enquanto o Spring reaproveita o contexto em cache, e as classes
  seguintes falham com "Failed to obtain JDBC Connection".
- **PostgreSQL 18+ exige o volume em `/var/lib/postgresql`**, não em
  `/var/lib/postgresql/data`; com o caminho antigo a imagem recusa iniciar.
- **Não testar o fuso do banco por `pg_settings`**: o driver JDBC envia
  `TimeZone` no pacote de inicialização, então tanto `setting` quanto
  `reset_val` passam a refletir a JVM. Testar fidelidade de instante em
  `timestamptz`. Os testes rodam com `-Duser.timezone=UTC`.
- **`defineConfig` do Vitest vem de `vitest/config`**, não de `vite`: só essa
  versão aceita a chave `test`.
- **`@AutoConfigureMockMvc` mudou de módulo e de pacote** no Spring Boot 4:
  exige `spring-boot-starter-webmvc-test` e fica em
  `org.springframework.boot.webmvc.test.autoconfigure`. O antigo
  `...boot.test.autoconfigure.web.servlet` não existe mais.
- **`@MockBean` foi removido** no Spring Boot 4; usar `@MockitoBean`.
- **Classe com dois construtores com argumentos** falha ao virar bean ("No
  default constructor found"). Anotar um deles com `@Autowired`.
- **MockMvc não aplica o `context-path`**, então `instance` sai como
  `/auth/login`, sem `/api/v1`. No servidor real o prefixo aparece. Não
  "corrigir" o código por causa disso.
- **`char(n)` no SQL vira `bpchar` e quebra a validação de schema** do
  Hibernate, que espera `varchar(n)` para um campo `String`. Usar `varchar`
  sempre. O `ddl-auto: validate` pega isso no primeiro teste de contexto.
- **`inet` não aceita parâmetro do driver JDBC** (chega como `varchar` e não há
  conversão implícita). Ver D18.
- **Jackson 3 recusa primitivo ausente no JSON** (`FAIL_ON_NULL_FOR_PRIMITIVES`
  agora é `true`). Um `boolean` opcional num record de request faz a rota
  responder 400 quando o campo não vem. Usar `Boolean` e tratar `null`.
- **`created_at` com `insertable = false` volta nulo após o `save`**: o valor
  vem do `DEFAULT now()` do banco e não é relido. A resposta do POST sai sem a
  data. As entidades preenchem `createdAt` pelo `Clock` no construtor.
- **Validar URL por regex é frágil.** A primeira versão aceitava
  `javascript:1,alert(1)`. Usar `@WebUrl` (parsing com `java.net.URI`) no
  backend e `safeHref` no frontend antes de montar qualquer `href`.
- **Teste de integração que faz login deve usar `TestSessions.login`.** O
  limitador é bean do contexto compartilhado entre classes; logins todos de
  127.0.0.1 esgotam a cota por rede conforme a suíte cresce, e os testes
  passam a falhar com 429 em ordem imprevisível.
- **`typescript-eslint` não suporta TypeScript 7** (peer `<6.1.0` em
  2026-11-05). O lint é o `oxlint` (`npm run lint`, avisos tratados como
  erro), que não depende do compilador. Não forçar ESLint com
  `--legacy-peer-deps`.
- **nginx corta corpo acima de 1 MB por padrão** (`client_max_body_size`), com
  413 antes de chegar ao backend. A importação aceita 2 MB (D14); o limite está
  em `frontend/nginx.conf`. Teste de upload só pelo MockMvc não pega isso.
- **Spring Data ignora repositório declarado como interface aninhada**
  (`considerNestedRepositories = false`). Um arquivo por repositório.
- **JSX descarta espaço no fim de linha seguido de quebra.** `{a}: ` numa linha
  e `{b}` na seguinte renderiza `a:b`. Montar o texto numa template string.
- **`curl -F file=@/c/...` no Git Bash não traduz o caminho do Windows**: a
  requisição falha sem mensagem. Rodar de dentro da pasta, com caminho
  relativo.
- **O cookie CSRF precisa de `setCookiePath("/")`.** Sem isso herda o
  context-path `/api/v1`, e o JavaScript da página servida em `/` não o vê: o
  login falha com 403 **no navegador**, embora todo teste de API passe. Há
  teste de regressão em `AuthControllerIntegrationTest`.
- **Cookie não é isolado por porta.** Todos os serviços em `localhost`
  compartilham o mesmo conjunto de cookies, e esta máquina roda outros projetos
  em 5173/8080/55433. Nome genérico (`XSRF-TOKEN`, `JSESSIONID`) colide, o
  navegador envia **dois cookies de mesmo nome**, o servidor lê só o primeiro e
  recusa com **403 sem mensagem útil**. Por isso os nomes são `JH_XSRF` /
  `X-JH-XSRF` e `JHSESSION`. Ver D22.
- **Mudar o path ou o nome de um cookie deixa órfão no navegador.** O antigo
  continua sendo enviado e pode ser lido no lugar do novo. Ao mexer em cookie,
  testar com o resíduo presente, não só com pote limpo.
- **Cookie CSRF só é reemitido quando o token é novo.** Asserção sobre
  `Set-Cookie` numa requisição HTTP fica dependente da ordem dos testes; testar
  o `CsrfTokenRepository` diretamente.
- **Portas 55107–55806 são reservadas pelo Windows/Hyper-V** nesta máquina
  (`netsh interface ipv4 show excludedportrange protocol=tcp`). Conferir antes
  de escolher porta nova.

## Portas (escolhidas para não colidir com outros projetos desta máquina)

| Serviço | Porta | Observação |
|---|---|---|
| **Aplicação (nginx)** | **8090** | **ponto de entrada em container; é aqui que você testa** |
| PostgreSQL | 15432 | 5432 é o Postgres local; faixas 55107–55806 são reservadas pelo Windows |
| Backend | 8081 | 8080 é o projeto `agendamento`; em container, só para depuração |
| Frontend (Vite dev) | 5174 | 5173 é o projeto `agendamento` |

## Autenticação (Etapa 3)

Sessão por cookie `JHSESSION` (`HttpOnly`, `SameSite=Lax`, `Secure` via
`COOKIE_SECURE`). CSRF em cookie `XSRF-TOKEN` legível por JavaScript, reenviado
no cabeçalho `X-XSRF-TOKEN` — o `httpClient` já faz isso em POST/PATCH/PUT/DELETE.

Rotas públicas: `/health`, `/auth/login`, `/openapi/**`, `/docs/**`.
Todo o resto exige autenticação, **inclusive rotas inexistentes**, que respondem
401 em vez de 404 para anônimos. Isso é intencional: não revela quais rotas
existem.

Para entrar em desenvolvimento: defina `LOCAL_ADMIN_PASSWORD` no `.env` e rode
com o perfil `local` (`SPRING_PROFILES_ACTIVE=local`). O `LocalBootstrap` cria a
organização e o administrador na primeira execução (D19).

Um recurso de outra organização responde **404, nunca 403**: informar que existe
mas pertence a outro tenant já é vazamento.

A associação é **revalidada no banco a cada requisição autenticada**
(`MembershipRevalidationFilter`, D21). Revogar associação, desabilitar usuário,
suspender organização ou mudar papel vale na requisição seguinte, sem esperar a
sessão expirar. Não substitua isso por cache sem reler a decisão D21.

O limitador de login usa **reserva atômica** (`tryAcquire`, D20). Nunca
reintroduzir um par `isBlocked()`/`recordFailure()`: entre a verificação e a
contabilização, requisições concorrentes furam o limite. O descarte por teto
**nunca remove entrada bloqueada** — sem isso, inundar o limitador apaga o
próprio bloqueio e devolve tentativas ilimitadas ao atacante. Há também um
limite por IP, porque o e-mail é escolhido por quem chama.

**Teste de limite precisa ser validado ao contrário:** remova a proteção e
confirme que o teste falha. Três versões do teste do limitador passavam sem a
proteção — uma não alcançava o teto, outra esgotava o teto de IPs primeiro,
outra usava relógio congelado, que deixa todas as entradas com o mesmo instante
e torna "mais antiga" sem significado.

## Versões fixadas (Etapa 2, consultadas nos registries em 2026-11-04)

Java 21 · Spring Boot 4.1.2 · SpringDoc 3.2.0 · Testcontainers 2.0.5 ·
PostgreSQL 18-alpine · React 19.3.1 · TypeScript 7.0.2 · Vite 8.3.5 ·
Vitest 5.1.0 · Tailwind 4.3.4.

Flyway, Jackson, Hibernate e o driver JDBC têm versão gerenciada pelo BOM do
Spring Boot — não fixar manualmente. Ao atualizar qualquer versão, consultar o
registry em vez de supor a última.

Conexão local: `postgresql://jhcomp@localhost:15432/jhcomp_prospect`.
O banco escuta apenas em `127.0.0.1`.

## Regras não negociáveis

Vêm da especificação e existem por razão legal ou de produto. Não contorne nem
"simplifique" nenhuma delas sem decisão registrada em `docs/11-decisions.md`.

1. **Nenhum envio externo automático.** A IA não tem caminho de envio,
   publicação, remoção de bloqueio de contato ou exclusão de registro
   (`docs/06` §11). Isso é restrição de arquitetura: a camada de IA não recebe
   ferramenta de escrita. Há teste estrutural para isso.
2. **`organization_id` em toda entidade privada**, e o contexto de organização
   vem **sempre** da sessão autenticada. Nunca aceitar `organizationId` do
   cliente como fonte de autorização. Validar que entidades relacionadas
   pertencem à mesma organização.
3. **"Não encontrado" não é "não existe".** `NOT_FOUND` nunca gera pontos de
   deficiência no score. Informação desconhecida não penaliza.
4. **Bloqueio de não contato é verificado antes de gerar, aprovar e registrar**
   mensagem, e permanece válido mesmo se o lead for reclassificado. Importação
   nunca remove bloqueio.
5. **`SENT` só com envio confirmado.** Abrir um aplicativo de mensagens não é
   envio. Aprovada não é enviada.
6. **Lead não vai para `CONTACTED` automaticamente** ao gerar ou aprovar
   mensagem. Só com confirmação do operador.
7. **Segredos fora do código** (RNF-006). Nunca commitar `.env`, token ou chave.
8. **Fetcher de URL protegido contra SSRF:** bloquear IP privado, loopback e
   endereço de metadata; aplicar timeout, limite de redirecionamentos e de
   tamanho de resposta (`docs/07` §6).
9. **Saída de IA e conteúdo de página externa são dados não confiáveis**, nunca
   instrução. Validar schema, limitar tamanho, não executar código retornado.
10. **Timestamps em UTC** (RNF-009); dinheiro em `NUMERIC`, nunca `FLOAT`.
11. **Migrations Flyway são imutáveis após aplicadas.** Nunca editar uma
    migration já aplicada; criar a próxima versão.
12. **Dados de teste são fictícios.** Nunca colocar dados pessoais reais ou
    listas reais de empresas em fixtures ou no repositório (`docs/10` §10).

## Convenções de código

**Backend**

- `domain` não depende de Spring, HTTP nem fornecedor externo.
- Entidade JPA nunca é exposta na API. DTOs de request/response explícitos.
- Controller não contém regra de negócio: valida o request e chama o caso de uso.
- Transação nos casos de uso que alteram múltiplas entidades. **Nunca manter
  transação aberta durante chamada de rede.**
- Erro no formato de `docs/05` §1. Nunca retornar stack trace.
- Integração externa só por adaptador (`BusinessSource`, `AIProvider`), com
  timeout e tratamento de rate limit.
- Toda listagem é paginada (RNF-002).

**Frontend**

- TypeScript `strict`. **`any` é proibido**; evitar cast e `!` não justificados.
- Cliente HTTP centralizado, nunca `fetch` espalhado.
- Toda tela prevê carregando, vazio, erro, sem permissão e sessão expirada
  (`docs/08` §11).
- Acessibilidade: navegação por teclado, foco visível, label em campo, não
  depender só de cor para status, confirmação em ação destrutiva.

**Evitar:** overengineering, abstração sem uso concreto, dependência sem
justificativa, duplicação, método ou componente grande demais, comentário que
repete o código, implementação provisória apresentada como definitiva.

## Definition of Done

Uma etapa só está concluída com: regra de negócio implementada, validação de
entrada, autorização verificada, tratamento de erro, testes adequados,
documentação atualizada, revisão de segurança/privacidade quando aplicável,
estados de UI cobertos e logs sem dado sensível desnecessário.

Teste negativo de acesso cruzado entre organizações é obrigatório em toda etapa
que adicione entidade ou endpoint privado.

## Ao concluir uma etapa

Informe: o que foi implementado, arquivos criados ou modificados, testes
executados, resultado real dos testes, problemas pendentes e próxima etapa.
Se um teste falhou, diga que falhou e mostre a saída.
