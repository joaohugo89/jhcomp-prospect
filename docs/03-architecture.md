# 03 — Arquitetura técnica

## 1. Decisão arquitetural

Adotar **monólito modular** com separação por domínio e camadas. Não iniciar com microserviços.

Motivos:
- Menor custo operacional.
- Deploy e depuração mais simples.
- Transações consistentes no banco relacional.
- Fronteiras de domínio explícitas.
- Possibilidade de extrair serviços no futuro se houver necessidade comprovada.

## 2. Stack proposta

### Backend
- Java 21.
- Spring Boot em versão estável compatível escolhida no início do projeto.
- Spring Web.
- Spring Security.
- Spring Data JPA.
- Jakarta Validation.
- Flyway.
- SpringDoc OpenAPI.
- JUnit 5 e Mockito.
- Testcontainers para testes de integração.

### Frontend
- React.
- TypeScript em modo strict.
- Vite.
- Tailwind CSS.
- Biblioteca de componentes escolhida e documentada pelo projeto.
- Cliente HTTP centralizado.
- Gerenciamento de estado local e de servidor sem duplicar responsabilidades.

### Infraestrutura
- PostgreSQL.
- Docker e Docker Compose para desenvolvimento.
- Reverse proxy/HTTPS na implantação.
- Serviço de armazenamento de artefatos apenas quando o Website Studio exigir.
- CI para build, lint e testes.

Não fixar versões arbitrárias neste documento. Fixar versões compatíveis no `pom.xml`, lockfile e imagens Docker, evitando tags flutuantes em produção.

## 3. Módulos de domínio

| Módulo | Responsabilidade |
|---|---|
| identity | Usuários, autenticação e papéis |
| organization | Organizações e associação de membros |
| company | Cadastro mestre de empresas |
| lead | Oportunidades comerciais |
| discovery | Consultas e importação de fontes |
| enrichment | Normalização e atualização de dados |
| analysis | Achados, evidências e score |
| crm | Pipeline, negócios, tarefas e interações |
| outreach | Rascunhos, aprovações e registros de contato |
| website | Templates, prévias e projetos |
| dashboard | Consultas agregadas e indicadores |
| shared | Configuração, erros, auditoria e utilitários transversais |

Os módulos devem depender de contratos explícitos. Evitar acesso direto às classes internas de outro módulo.

## 4. Organização interna de cada módulo

Estrutura recomendada:

```text
module/
├── domain/
│   ├── model/
│   ├── valueobject/
│   ├── repository/
│   └── service/
├── application/
│   ├── usecase/
│   ├── command/
│   ├── query/
│   └── dto/
├── infrastructure/
│   ├── persistence/
│   ├── client/
│   └── adapter/
└── presentation/
    ├── controller/
    └── request/
```

A estrutura pode ser simplificada em módulos pequenos, desde que as dependências continuem claras.

## 5. Regras de dependência

- `presentation` recebe HTTP, valida requests e chama casos de uso.
- `application` coordena os casos de uso.
- `domain` contém regras de negócio e não depende de Spring, HTTP ou fornecedor externo.
- `infrastructure` implementa persistência e integrações.
- Controladores não devem conter regras de negócio.
- Entidades JPA não devem ser expostas diretamente na API.
- DTOs de request/response devem ser explícitos.
- Não utilizar `any` no frontend.
- Não aceitar `organizationId` enviado pelo cliente como fonte de autorização.

## 6. Integrações por adaptadores

### Fontes de empresas

Criar contrato semelhante a:

```java
public interface BusinessSource {
    SourceSearchResult search(SourceSearchCriteria criteria);
}
```

Cada fornecedor deve ter adaptador próprio, com:
- configuração e credenciais fora do código;
- timeout;
- tratamento de rate limit;
- mapeamento para modelo interno;
- proveniência;
- respeito aos termos de uso e retenção de dados.

A integração inicial deve ser escolhida após validar cobertura, preço, permissões e condições de armazenamento. A Places API oficial é uma candidata, não uma dependência obrigatória.

### IA

```java
public interface AIProvider {
    MessageGenerationResult generateMessage(
        MessageGenerationRequest request
    );

    LeadAnalysisResult analyzeLead(
        LeadAnalysisRequest request
    );
}
```

O resultado da IA deve ser tratado como conteúdo não confiável:
- validar estrutura;
- limitar tamanho;
- não executar código retornado;
- não permitir que a IA acione envio externo;
- registrar provedor/modelo quando apropriado;
- armazenar versão do prompt para rastreabilidade, sem guardar segredos.

### Mensageria

O MVP prepara mensagens e registra contatos manuais. Futuras integrações devem usar APIs oficiais, permissões adequadas e controles de envio.

## 7. Processamento assíncrono

Análises, importações maiores e geração de prévias podem ser executadas em background.

No início, avaliar `@Async` ou uma tabela de jobs persistente com worker Spring. Uma tabela de jobs é preferível quando for necessário sobreviver a reinicializações e retomar tarefas.

Não introduzir Redis, RabbitMQ ou Kafka sem necessidade demonstrada. Se a carga crescer, selecionar broker conforme requisitos reais.

Cada job deve ter:
- id;
- organização;
- tipo;
- payload validado;
- status;
- tentativas;
- timestamps;
- erro resumido;
- idempotency key quando aplicável.

## 8. Multi-tenancy

Usar isolamento lógico por `organization_id` no PostgreSQL no MVP.

Requisitos:
- coluna obrigatória em todas as entidades pertencentes à organização;
- filtro aplicado no backend em todas as consultas;
- contexto de organização derivado da sessão autenticada;
- índices compostos iniciando por `organization_id` quando fizer sentido;
- testes negativos de acesso cruzado;
- avaliar Row-Level Security como defesa adicional, não como substituto da autorização da aplicação.

Entidades globais, como catálogo de templates públicos, devem ser explicitamente identificadas como globais e não misturadas com dados privados.

## 9. Transações e consistência

- Usar transações nos casos de uso que alteram múltiplas entidades relacionadas.
- Não manter transação aberta durante chamadas de rede.
- Para integrações externas, persistir estado intermediário e processar resultado depois.
- Implementar idempotência em importações e jobs quando houver risco de repetição.
- Registrar eventos importantes no histórico comercial.

## 10. Frontend

Estrutura sugerida:

```text
src/
├── app/
│   ├── router/
│   └── providers/
├── modules/
│   ├── dashboard/
│   ├── companies/
│   ├── leads/
│   ├── discovery/
│   ├── analysis/
│   ├── pipeline/
│   ├── outreach/
│   └── website-studio/
├── components/
├── services/
├── hooks/
├── types/
└── shared/
```

Cada módulo deve concentrar páginas, componentes e tipos específicos. Componentes realmente reutilizáveis ficam em `components/`.

## 11. Observabilidade

MVP:
- logs estruturados;
- correlation/request ID;
- logs de falhas de integração sem segredos;
- health checks;
- métricas básicas de jobs;
- auditoria de ações relevantes.

Futuro:
- tracing distribuído se a arquitetura justificar;
- dashboards operacionais;
- alertas de falhas e custos.

## 12. Ambientes e deploy

Ambientes:
- local;
- teste/CI;
- produção.

Usar Docker Compose no desenvolvimento para API e PostgreSQL. Frontend pode ser executado separadamente.

Produção deve ter:
- HTTPS;
- variáveis/segredos protegidos;
- backup e teste de restauração;
- migrations controladas;
- política de retenção;
- monitoramento de disponibilidade.

Cloudflare pode atender DNS, proxy e hospedagem do frontend conforme a solução escolhida. Não presumir que substitui a hospedagem do backend ou do banco.
