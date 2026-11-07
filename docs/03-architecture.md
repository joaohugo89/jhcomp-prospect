# 03 — Technical architecture

## 1. Architectural decision

A **modular monolith**, split by domain and by layer. Not microservices.

Reasons:

- lower operational cost for a one-person team;
- simpler deployment and debugging;
- consistent transactions in a relational database;
- explicit domain boundaries;
- the option to extract a service later, if a need is ever demonstrated.

## 2. Stack

Versions are pinned in `pom.xml`, the lockfile and the Docker images, never as
floating tags. The current set was checked against the registries on
2026-11-04: Java 21 · Spring Boot 4.1.2 · SpringDoc 3.2.0 · Testcontainers
2.0.5 · PostgreSQL 18-alpine · React 19.3.1 · TypeScript 7.0.2 · Vite 8.3.5 ·
Vitest 5.1.0 · Tailwind 4.3.4. Flyway, Jackson, Hibernate and the JDBC driver
are managed by the Spring Boot BOM and must not be pinned by hand.

### Backend

Java 21 · Spring Boot (Web, Security, Data JPA, Validation) · Flyway ·
SpringDoc OpenAPI · JUnit 5 · Mockito · Testcontainers for integration tests
against a real PostgreSQL.

### Frontend

React · TypeScript in strict mode (`any` is forbidden) · Vite · Tailwind CSS ·
a single centralised HTTP client · local and server state kept apart rather
than duplicated.

### Infrastructure

PostgreSQL · Docker and Docker Compose for development · a reverse proxy
terminating HTTPS in production · CI for build, lint and tests.

## 3. Domain modules

The original plan listed twelve modules. The implementation has **eight**
(D16): `enrichment` lives inside `company` and `organization` inside
`identity`, because neither had a domain of its own; `website` was dropped
with the Website Studio (D13).

| Module | Responsibility |
|---|---|
| `identity` | Organisations, users, membership, authentication, roles |
| `company` | Master company record, provenance, deduplication, enrichment |
| `lead` | Commercial opportunities, status, priority, saved views |
| `discovery` | CSV import and duplicate classification |
| `analysis` | Findings, evidence, score |
| `crm` | Pipeline, deals, tasks, interactions |
| `outreach` | Drafts, approvals, contact records, do-not-contact list |
| `dashboard` | Aggregate queries and indicators |
| `shared` | Configuration, errors, auditing, jobs, normalisation, time |

Modules depend on explicit contracts. Reaching into another module's internal
classes is not allowed.

## 4. Layout inside a module

```text
module/
├── domain/           entities, value objects, repository ports, domain services
├── application/      use cases, commands, queries, DTOs
├── infrastructure/   persistence, clients, adapters
└── presentation/     controllers, request and response records
```

Only `company`, `lead`, `analysis` and `outreach` carry all four layers.
`discovery` and `dashboard` are flat, and that is deliberate: a package
structure that exists only to be symmetrical costs navigation and buys
nothing.

## 5. Dependency rules

- `presentation` receives HTTP, validates requests and calls a use case.
- `application` coordinates use cases.
- `domain` holds business rules and depends on neither Spring, nor HTTP, nor
  any external provider.
- `infrastructure` implements persistence and integrations.
- Controllers contain no business logic.
- JPA entities are never exposed by the API; request and response DTOs are
  explicit.
- `any` is forbidden in the frontend.
- `organizationId` from the client is never a source of authorisation.

## 6. Integrations through adapters

### Business sources

```java
public interface BusinessSource {
    SourceSearchResult search(SourceSearchCriteria criteria);
}
```

Each provider gets its own adapter, with configuration and credentials outside
the code, a timeout, rate-limit handling, mapping to the internal model,
provenance, and respect for the provider's terms and retention rules.

**No provider has been chosen and no adapter exists.** The first one should be
selected only after validating regional coverage, price, permissions and
storage conditions. The official Places API is a candidate, not a commitment.

### AI

```java
public interface AIProvider {
    MessageGenerationResult generateMessage(MessageGenerationRequest request);
}
```

The only implementation in the MVP is a deterministic fake with no network
access (D26). It builds the text from the supplied context and returns the same
JSON shape a model would, so output validation is genuinely exercised.

`analyzeLead` was part of the original sketch and is **not** part of the port.
The analysis is deterministic and evidence-based; handing it to a model would
have meant scoring companies on an assertion nobody could trace.

AI output is untrusted input. Validate its structure, cap its size, never
execute returned code, never give the AI a path to send anything externally,
record provider and model where appropriate, and store the prompt version for
traceability without storing secrets.

### Messaging

The MVP prepares messages and records manual contacts. Any future integration
must use official APIs with the right permissions and sending controls.

## 7. Asynchronous processing

Analyses and other slow work run in the background through a **persistent job
table with a Spring worker** (D15), not `@Async`: the work has to survive a
restart and be resumable.

No Redis, RabbitMQ or Kafka. If load ever justifies a broker, pick one against
the requirements at that time.

Each job records an id, the organisation, a type, a validated payload, a
status, an attempt count, timestamps, a summarised error, and an idempotency
key where applicable.

## 8. Multi-tenancy

Logical isolation by `organization_id` in PostgreSQL.

- The column is mandatory on every entity that belongs to an organisation.
- The filter is applied in the backend on every query.
- The organisation context comes from the authenticated session, and
  membership is revalidated on each request (D21).
- Composite indexes start with `organization_id` where it helps.
- Negative cross-tenant tests are mandatory.
- A resource from another organisation answers `404`, never `403`.
- Row-Level Security is worth evaluating as defence in depth, never as a
  replacement for application authorisation.

## 9. Transactions and consistency

- Use a transaction in any use case that changes several related entities.
- **Never hold a transaction open across a network call.**
- For external integrations, persist the intermediate state and process the
  result afterwards.
- Make imports and jobs idempotent wherever a repeat is possible.
- Record meaningful events in the commercial history.

One trap worth stating explicitly: `SELECT ... FOR UPDATE` combined with
auditing in a `REQUIRES_NEW` transaction deadlocks, because `audit_event` has a
foreign key to `organization` and the new transaction waits on the one that
started it. Serialise with `pg_advisory_xact_lock` instead.

## 10. Frontend

```text
src/
├── app/          routing, filters, formatting, preferences, theme
├── components/   genuinely reusable components
├── modules/      one folder per area: companies, leads, crm, analysis,
│                 outreach, imports, dashboard, members, auth
├── services/     one module per API area, over a single HTTP client
└── types/        the API contract mirrored as TypeScript types
```

Each module owns its pages, components and local types. Routes are split into
separate chunks so opening the application does not download the screens it is
not showing.

## 11. Observability

Today: structured logs, a request id that also returns in the `X-Request-Id`
header, integration failures logged without secrets, a health check that
verifies the database, basic job metrics, and an audit trail for relevant
actions.

Later: distributed tracing if the architecture ever justifies it, operational
dashboards, failure and cost alerts.

## 12. Environments and deployment

Three environments: local, CI, production.

Development runs on Docker Compose. In production (D27) the stack is defined by
`docker-compose.prod.yml`, only Caddy publishes a port, and the `prod` profile
trusts `X-Forwarded-For` only from an internal proxy — without that, the
per-IP login limit would apply to every user at once. The first administrator
is created by `scripts/create-admin.sh` under the `bootstrap` profile.

Production requires HTTPS, protected secrets, backups with a **tested**
restore, controlled migrations, a retention policy and availability
monitoring. Operational procedures are in document 12.
