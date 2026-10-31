# JH.COMP Prospect

Plataforma interna de prospecção comercial, inteligência de leads, CRM e gestão de projetos digitais da JH.COMP, com arquitetura preparada para futura oferta SaaS.

## Objetivo

Ajudar a JH.COMP a encontrar empresas em Poços de Caldas e região, organizar oportunidades, analisar a presença digital, preparar abordagens personalizadas e acompanhar o processo comercial até a contratação de serviços digitais.

## Princípios do produto

1. **Utilidade comercial antes de sofisticação:** o MVP deve ajudar a executar o processo comercial real.
2. **Human-in-the-loop:** pesquisas, análises e mensagens assistidas por IA devem ser revisáveis; nenhum contato externo é enviado sem aprovação humana.
3. **Evidência antes de inferência:** diferenciar informação confirmada, não encontrada e não verificada.
4. **Privacidade desde a concepção:** registrar origem, finalidade, base legal avaliada e pedidos de oposição/não contato.
5. **Modularidade sem complexidade prematura:** monólito modular, não microserviços.
6. **Preparação para SaaS:** isolamento por organização desde o início, sem implementar cobrança no MVP.

## Documentação

| Documento | Conteúdo |
|---|---|
| [01 - Visão do produto](docs/01-product-overview.md) | Problema, objetivos, usuários, escopo e métricas |
| [02 - Requisitos funcionais](docs/02-functional-requirements.md) | Requisitos funcionais e critérios de aceite |
| [03 - Arquitetura](docs/03-architecture.md) | Stack, módulos, camadas e integrações |
| [04 - Banco de dados](docs/04-database-design.md) | Entidades, relacionamentos e regras de persistência |
| [05 - API](docs/05-api-specification.md) | Endpoints iniciais, contratos e convenções |
| [06 - Regras de negócio](docs/06-business-rules.md) | Ciclo do lead, score, deduplicação e pipeline |
| [07 - Segurança e LGPD](docs/07-security-and-lgpd.md) | Segurança, privacidade e controles operacionais |
| [08 - UI/UX](docs/08-ui-ux-specification.md) | Navegação, telas e comportamento |
| [09 - Roadmap do MVP](docs/09-mvp-roadmap.md) | Escopo, fases, prioridades e entregas |
| [10 - Estratégia de testes](docs/10-testing-strategy.md) | Pirâmide de testes, cenários e qualidade |
| [11 - Registro de decisões](docs/11-decisions.md) | Decisões que resolvem lacunas dos documentos 01–10 |
| [12 - Operação em produção](docs/12-operations.md) | Deploy, primeiro administrador, backup, restauração e monitoramento |

## Escopo do MVP

- Autenticação e organização.
- Cadastro de empresas e leads.
- Importação CSV com validação e relatório.
- Pesquisa manual e uma integração inicial de fonte de empresas, sujeita a disponibilidade, custos e termos de uso.
- Deduplicação e proveniência dos dados.
- Análise assistida da presença digital com evidências.
- Lead score determinístico e explicável.
- Pipeline comercial Kanban.
- Geração de mensagens com IA para revisão e aprovação.
- Registro de interações e lista de não contato.
- Seleção de template e prévia básica de site, sem publicação automática.

## Fora do MVP

- Disparo em massa ou cadências automáticas.
- Scraping de interfaces de plataformas.
- Editor visual completo.
- Geração e execução de código arbitrário por IA.
- Cobrança, planos e faturamento SaaS.
- Aplicativo mobile.
- Microserviços.

## Stack proposta

- Frontend: React + TypeScript.
- Backend: Java 21 + Spring Boot.
- Persistência: PostgreSQL + Spring Data JPA + Flyway.
- Segurança: Spring Security.
- Testes: JUnit 5, Mockito e Testcontainers.
- Execução: Docker.
- IA: adaptador `AIProvider`, desacoplado do fornecedor.
- Integrações: adaptadores específicos para fontes autorizadas e canais de comunicação.

As versões exatas das dependências devem ser escolhidas e fixadas no início da implementação, considerando versões estáveis e compatíveis no momento do desenvolvimento.

## Como executar

```bash
cp .env.example .env            # defina POSTGRES_PASSWORD e LOCAL_ADMIN_PASSWORD
docker compose up -d --build
```

Aplicação em http://localhost:8090. O frontend e a API ficam na mesma origem:
o nginx do frontend faz proxy de `/api/v1` para o backend.

Testes end-to-end (com o ambiente acima no ar):

```bash
cd frontend && npx playwright install chromium
E2E_ADMIN_PASSWORD=<LOCAL_ADMIN_PASSWORD do .env> npm run e2e
```

Produção (VPS Linux com Caddy e HTTPS automático): ver
[docs/12-operations.md](docs/12-operations.md).

Detalhes de comandos, portas e convenções em [CLAUDE.md](CLAUDE.md).

## Como usar com Claude Code

1. Leia este README e os documentos em `docs/`.
2. Implemente apenas a fase/sprint solicitada.
3. Antes de alterar um contrato documentado, proponha a mudança e atualize a documentação correspondente.
4. Não invente integrações, credenciais, dados de empresas ou resultados de pesquisa.
5. Escreva testes para regras de negócio e isolamento entre organizações.
6. Não implemente envio externo automático sem requisito explícito, revisão de privacidade e aprovação humana.
7. Ao concluir cada etapa, informe arquivos alterados, decisões, testes executados e pendências.

## Primeira entrega verificável

Importar um conjunto de empresas obtido legitimamente, evitar duplicatas, qualificá-las, visualizar oportunidades, preparar mensagens para revisão e acompanhar as interações no CRM.

> Este repositório é a especificação inicial. Decisões técnicas que dependam de fornecedor, preço, disponibilidade de API ou validação jurídica devem ser confirmadas antes da implementação.
