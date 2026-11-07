# JH.COMP Prospect

Internal sales prospecting tool for JH.COMP, a small IT services company in
Poços de Caldas, Brazil. It centralises company and lead records, imports data
with deduplication and provenance, scores digital-presence opportunities from
evidence, tracks the commercial pipeline, and drafts outreach messages for a
human to review.

Java 21 · Spring Boot 4 · PostgreSQL 18 · React 19 · TypeScript 7 · Docker

> **The user interface is in Portuguese on purpose** — it is an internal tool
> for a Brazilian operator. Code, comments and documentation are in English.

---

## Why this repository may be worth reading

It is a complete, working system rather than a tutorial project, and the parts
that are usually skipped are the ones that got the most attention:

- **Multi-tenancy that is actually enforced.** Every private entity carries an
  `organization_id`, the organisation always comes from the authenticated
  session and never from the request, and a resource belonging to another
  tenant answers `404` rather than `403` — saying "it exists but is not yours"
  is already a leak. Every endpoint has a negative cross-tenant test.
- **A decision log.** [`docs/11-decisions.md`](docs/11-decisions.md) records 32
  decisions (D1–D32) that resolved real ambiguities: what the single source of
  commercial state is, how the CSV import behaves on duplicates, what the login
  rate limiter must guarantee. Each one states the problem, the options and the
  reason.
- **Security controls with a rationale, and tests that would fail without
  them.** The login rate limiter uses atomic reservation rather than a
  check-then-record pair, and its eviction never drops a blocked entry —
  otherwise flooding the limiter would erase the attacker's own block. The test
  for it was validated in reverse: remove the protection, confirm the test
  fails.
- **Privacy as a constraint, not a feature.** The AI layer has no write tool
  and therefore no path to send, publish, unblock a contact or delete a record;
  a structural test enforces it. Do-not-contact entries are checked before
  generating, approving and recording a message, and are never removed by an
  import.
- **Pitfalls written down.** [`CLAUDE.md`](CLAUDE.md) lists every trap this
  stack cost real time on — Spring Boot 4 moving to Jackson 3, Flyway silently
  applying no migrations without the right starter, cookies not being isolated
  by port on `localhost`. It is the file a new contributor should read first.

## Running it

```bash
cp .env.example .env            # set POSTGRES_PASSWORD and LOCAL_ADMIN_PASSWORD
docker compose up -d --build
```

The application is at **http://localhost:8090**. The frontend's nginx proxies
`/api/v1` to the backend, so both are served from the same origin — which is
what the session cookie (`SameSite=Lax`, decision D1) requires in order to work
without CORS credentials.

On first start, the `local` profile creates the organisation and the first
administrator from `LOCAL_ADMIN_EMAIL` / `LOCAL_ADMIN_PASSWORD` (decision D19).

<details>
<summary>Running without containers, for faster iteration</summary>

```bash
docker compose up -d postgres                       # database only

cd backend  && mvn verify                           # compile + tests (Testcontainers)
cd backend  && mvn spring-boot:run                  # API on :8081, base path /api/v1
cd frontend && npm run dev                          # UI on :5174
```

</details>

End-to-end tests, with the containerised environment up:

```bash
cd frontend && npx playwright install chromium
E2E_ADMIN_PASSWORD=<LOCAL_ADMIN_PASSWORD from .env> npm run e2e
```

Production deployment (Linux VPS, Caddy with automatic HTTPS, verified
backups): [`docs/12-operations.md`](docs/12-operations.md).

## What it does

| Capability | Notes |
|---|---|
| Companies and leads | Full CRUD, paginated lists, saved views, work queue |
| CSV import | Two phases: analysing creates nothing; confirming imports valid rows, chosen duplicates, and fills only the empty fields of existing companies (D23). 2 MB / 2,000 rows |
| Deduplication | Normalised phone, e-mail and name matching, with operator review |
| Provenance | Every company records where each piece of data came from and when |
| Digital presence analysis | Findings with evidence, confidence and verification state; runs as a background job |
| Lead score | Deterministic and explainable. A criterion only scores when `CONFIRMED`; "not found" never counts as a confirmed deficiency |
| Pipeline | Kanban over `Lead.status`, which is the single source of commercial state (D2) |
| Outreach | AI drafts a message, a human edits and approves, and sending is recorded manually. Nothing leaves the system automatically |
| Do-not-contact list | Checked before generating, approving and recording a send; never cleared by an import |
| Dashboard | Funnel, conversion by segment, attention signals, recent activity |
| Members | Admin invites, temporary password, forced change, session revocation on credential change (D28) |

Deliberately **not** in scope: mass sending or automated cadences, scraping
platform interfaces, arbitrary code generation or execution by AI, SaaS billing,
a mobile app, microservices.

## Architecture

A modular monolith. Eight modules, each owning its tables and exposing its own
HTTP surface; `company`, `lead`, `analysis` and `outreach` carry the full four
layers (`domain`, `application`, `infrastructure`, `presentation`), while
`discovery` and `dashboard` are flat because they have no domain of their own
(D16).

```
backend/src/main/java/com/jhcomp/prospect/
├── identity/     organisations, users, membership, authentication
├── company/      companies, provenance, deduplication, enrichment
├── lead/         leads, status, priority, saved views
├── discovery/    CSV import and duplicate classification
├── analysis/     digital presence findings, evidence, score
├── crm/          pipeline, deals, interactions, tasks
├── outreach/     message drafts, approval, do-not-contact list
├── dashboard/    read-only aggregates
└── shared/       security, auditing, errors, jobs, normalisation, time
```

Rules the code is held to: the domain layer depends on neither Spring nor HTTP;
JPA entities are never exposed by the API; controllers validate and delegate;
no transaction is ever held open across a network call; every external
integration goes through an adapter with a timeout.

## Documentation

| Document | Contents |
|---|---|
| [01 — Product overview](docs/01-product-overview.md) | Problem, objectives, users, scope, metrics |
| [02 — Functional requirements](docs/02-functional-requirements.md) | Requirements and acceptance criteria |
| [03 — Architecture](docs/03-architecture.md) | Stack, modules, layers, integrations |
| [04 — Database design](docs/04-database-design.md) | Entities, relationships, indexes |
| [05 — API specification](docs/05-api-specification.md) | Endpoints, contracts, conventions |
| [06 — Business rules](docs/06-business-rules.md) | Lead lifecycle, score, deduplication, pipeline |
| [07 — Security and privacy](docs/07-security-and-lgpd.md) | Controls, LGPD, operational safeguards |
| [08 — UI/UX](docs/08-ui-ux-specification.md) | Navigation, screens, states, accessibility |
| [09 — Roadmap](docs/09-mvp-roadmap.md) | Scope, phases, priorities, deliverables |
| [10 — Testing strategy](docs/10-testing-strategy.md) | Test pyramid, scenarios, quality bar |
| [11 — Decision log](docs/11-decisions.md) | D1–D32, with the reasoning behind each |
| [12 — Operations](docs/12-operations.md) | Deploy, first admin, backup, restore, monitoring |

[`CLAUDE.md`](CLAUDE.md) holds the working conventions, the port map and the
stack pitfalls already paid for.

## Tests

| Suite | Count | How to run |
|---|---|---|
| Backend (unit + integration, real PostgreSQL via Testcontainers) | 440 | `cd backend && mvn verify` |
| Frontend (Vitest + Testing Library) | 169 | `cd frontend && npm test -- --run` |
| End-to-end (Playwright, two critical journeys) | 2 | `cd frontend && npm run e2e` |

A step is only done when the business rule, input validation, authorisation,
error handling, tests, documentation and UI states are all in place — and a
negative cross-tenant test is mandatory for any step that adds a private entity
or endpoint.

## Status

The MVP is implemented and running. The honest gaps, kept visible rather than
hidden: there is no business-data source adapter yet, so leads enter by CSV or
by hand; the only AI provider is a deterministic fake (D26), which is what the
`AIProvider` port was designed to make replaceable; and the Website Studio was
removed from scope by decision D13.
