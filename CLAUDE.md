# CLAUDE.md — JH.COMP Prospect

Internal sales prospecting tool for JH.COMP (Poços de Caldas, Brazil).
Modular monolith: Java 21 + Spring Boot, React + TypeScript frontend,
PostgreSQL.

**Language rule:** code, comments, documentation and commit messages are in
**English**. Everything the user reads on screen is in **Portuguese** — this is
an internal tool for a Brazilian operator. Do not translate UI strings.

## Before implementing

The specification lives in `docs/`. Read **only the documents relevant to the
current task**, not all of them every session.

| I need… | Read |
|---|---|
| A requirement and its acceptance criteria | `docs/02-functional-requirements.md` |
| Layers, modules, adapters | `docs/03-architecture.md` |
| Entities and indexes | `docs/04-database-design.md` |
| An endpoint contract | `docs/05-api-specification.md` |
| A business rule, the score, deduplication | `docs/06-business-rules.md` |
| A security or LGPD control | `docs/07-security-and-lgpd.md` |
| A screen, component or UI state | `docs/08-ui-ux-specification.md` |
| The scope of a stage | `docs/09-mvp-roadmap.md` |
| What to test | `docs/10-testing-strategy.md` |
| **Decisions that override the documents** | **`docs/11-decisions.md`** |
| Deployment and operations | `docs/12-operations.md` |

`docs/11-decisions.md` prevails over the rest. Consult it before following any
point documents 01–10 leave open — the known ambiguities are resolved there
(D1 to D32). Do not reopen a decision marked `Approved`.

## Decisions in force (summary; detail in `docs/11-decisions.md`)

- **D1** Session authentication: `HttpOnly`/`Secure`/`SameSite=Lax` cookie plus
  CSRF. No JWT, no refresh token. `/auth/refresh` does not exist.
- **D2** `Lead.status` is the single source of commercial state. Kanban columns
  map 1:1 to it. Moving a card is `PATCH /leads/{leadId}/status`. `PATCH
  /deals/{dealId}/stage` does not exist. `Deal` holds only value, service,
  outcome and loss reason.
- **D13** Website Studio is out of the MVP. Do not create `WebsiteTemplate`,
  `WebsiteProject` or `WebsiteDeployment`.
- **D23** Two-phase CSV import: analysing creates no company; confirming
  imports the valid rows and the chosen duplicates, and completes existing
  companies only in their empty fields. D14 limits: 2 MB, 2,000 rows.
- **D24** Score v1: a criterion scores only when `CONFIRMED`; the absence of a
  website is never confirmed automatically. Website checks go through
  `SafeHttpFetcher` only (SSRF, deadlines, limits, robots.txt). Analysis runs
  as a job (D15); the lead takes the score of the latest completed analysis
  (D3).
- **D26** Outreach: the AI returns text only (`AIProvider.generateMessage`, no
  write tool; structural test). The MVP has only the fake provider. Generation
  is synchronous with no transaction held during the call, and the output is
  validated before becoming a draft. Do-not-contact is checked by normalised
  identifier when generating, approving and recording a send; no removal in the
  MVP.
- **D27** Production: VPS with `docker-compose.prod.yml`, only Caddy publishes
  a port. The `prod` profile trusts `X-Forwarded-For` only from an internal
  proxy (without it the per-IP login limit applies to everyone at once). First
  admin via `scripts/create-admin.sh` (`bootstrap` profile). Operations in
  `docs/12`.
- **D28** Members: the admin registers a member and receives a temporary
  password, shown once; with it the session reaches only `/auth/me`,
  `/auth/logout` and `/auth/password`. Changing or resetting a password ends
  the other sessions (`credentials_changed_at`, checked during D21
  revalidation). Nobody changes their own membership.
- **D29** Dashboard: a company's origin is its first provenance; a rate needs a
  minimum sample of 10. **D30** Attention: idle 7 days, proposal without
  contact 7, no interaction 14 (identical in the Kanban, the list and the
  dashboard); a lead in `DO_NOT_CONTACT` never appears; `/team` returns id and
  name only. **D31** The lead list is a work queue (`/leads/worklist`, CRM
  module); bulk changes cover owner and priority only; private and shared
  views (only an admin publishes or deletes); a segment is stored in the
  spelling that already exists and compared case-insensitively.
- **D32** A company is deleted only without commercial activity (at most the
  `NEW` lead opened with it): by whoever registered it, within 7 days, or by an
  admin; otherwise archive. Undoing an import uses the same rule; `MERGED` rows
  do not change. Merging moves leads and sources and archives the origin; two
  open leads → 409.
- **D16** Eight modules, not twelve: `enrichment` inside `company`,
  `organization` inside `identity`. All four layers only in `company`, `lead`,
  `analysis` and `outreach`; `discovery` and `dashboard` are flat.

## Commands

### Full containerised environment (the recommended way to test)

```bash
cp .env.example .env                  # once; set LOCAL_ADMIN_PASSWORD
docker compose up -d --build          # postgres + backend + frontend
docker compose logs -f backend
docker compose ps                     # check each service's health
docker compose down                   # stop, keeping the data
docker compose down -v                # drop the database volume
```

The application is at **http://localhost:8090**. The frontend's nginx proxies
`/api/v1` to the backend, keeping both on the **same origin** — which is what
D1 requires for the `SameSite=Lax` cookie to work without CORS credentials.
Port 8081 is published only for debugging with `curl`; it must not be exposed
in production.

After changing backend or frontend code: `docker compose up -d --build`.

### Without containers (faster iteration)

```bash
docker compose up -d postgres         # database only

# Backend (spring-boot:run needs POSTGRES_PASSWORD in the environment)
cd backend  && mvn verify                 # compile + tests (PostgreSQL via Testcontainers)
cd backend  && mvn spring-boot:run        # API on :8081, base path /api/v1
cd frontend && npm run dev                # UI on :5174
cd frontend && npm run typecheck && npm run lint && npm test -- --run && npm run build
```

Quick check: `curl http://localhost:8081/api/v1/health` (verifies the database;
503 when it is down).

### End-to-end and operations

```bash
cd frontend && E2E_ADMIN_PASSWORD=... npm run e2e   # 2 flows (D17), containerised environment up
scripts/backup.sh                                    # dump + SHA-256 + retention
scripts/verify-backup.sh <dump> --compare            # restore into a disposable database and compare
scripts/restore.sh <dump> --yes                      # ERASES the current database
```

**The end-to-end suite writes to the database it runs against.** Once the
environment holds real prospecting data, give the suite its own environment.

Rehearsing the production compose on this machine: `APP_DOMAIN=localhost`,
`HTTP_PORT=8180`, `HTTPS_PORT=8443`, `docker compose -p jhcomp-rehearsal -f
docker-compose.prod.yml --env-file <file outside the repository>`, and
Playwright with `E2E_IGNORE_HTTPS_ERRORS=true`. Tear down with `down -v` in the
same project.

OpenAPI at `/api/v1/openapi`, Swagger UI at `/api/v1/docs`.

Project skills are not versioned (only `skills-lock.json`, with each one's
hash). In a fresh clone: `npx skills install`. Installed:
`spring-boot-rest-api-standards`, `spring-boot-test-patterns`,
`react-patterns`, `kpi-dashboard-design`.

## Pitfalls already hit in this stack

Each one cost real time. Do not reintroduce them.

- **Spring Boot 4 uses Jackson 3** (package `tools.jackson`). Jackson 2
  properties such as `spring.jackson.serialization.write-dates-as-timestamps`
  **no longer exist** and make the context fail to start. Dates already
  serialise as ISO-8601 by default.
- **Flyway needs `spring-boot-starter-flyway`** on Spring Boot 4. Declaring
  only `flyway-core`, as on Boot 3, puts the library on the classpath without
  enabling the autoconfiguration: the application starts and **no migration is
  applied, silently**. Always confirm the `Successfully applied N migrations`
  line.
- **Testcontainers 2.x prefixes its artifacts** with `testcontainers-`
  (`testcontainers-postgresql`, not `postgresql`), and the Spring Boot 4 BOM no
  longer manages their versions: import `testcontainers-bom`.
- **The test container must be a singleton**, started in a static block without
  `@Testcontainers`/`@Container`. With that extension the container stops at
  the end of each class while Spring reuses the cached context, and the
  following classes fail with "Failed to obtain JDBC Connection".
- **PostgreSQL 18+ needs the volume at `/var/lib/postgresql`**, not
  `/var/lib/postgresql/data`; with the old path the image refuses to start.
- **Do not test the database time zone through `pg_settings`**: the JDBC driver
  sends `TimeZone` in the startup packet, so both `setting` and `reset_val`
  reflect the JVM. Test instant fidelity in `timestamptz` instead. Tests run
  with `-Duser.timezone=UTC`.
- **Vitest's `defineConfig` comes from `vitest/config`**, not from `vite`: only
  that version accepts the `test` key.
- **`@AutoConfigureMockMvc` changed module and package** in Spring Boot 4: it
  needs `spring-boot-starter-webmvc-test` and lives in
  `org.springframework.boot.webmvc.test.autoconfigure`. The old
  `...boot.test.autoconfigure.web.servlet` is gone.
- **`@MockBean` was removed** in Spring Boot 4; use `@MockitoBean`.
- **A class with two argument-taking constructors** fails to become a bean ("No
  default constructor found"). Annotate one with `@Autowired`.
- **MockMvc does not apply the `context-path`**, so `instance` comes out as
  `/auth/login` without `/api/v1`. On the real server the prefix appears. Do
  not "fix" the code because of this.
- **`char(n)` in SQL becomes `bpchar` and breaks Hibernate's schema
  validation**, which expects `varchar(n)` for a `String` field. Always use
  `varchar`. `ddl-auto: validate` catches it in the first context test.
- **`inet` does not accept a JDBC driver parameter** (it arrives as `varchar`
  with no implicit conversion). See D18.
- **Jackson 3 refuses a missing primitive in JSON**
  (`FAIL_ON_NULL_FOR_PRIMITIVES` is now `true`). An optional `boolean` in a
  request record makes the route answer 400 when the field is absent. Use
  `Boolean` and handle `null`.
- **`created_at` with `insertable = false` comes back null after `save`**: the
  value comes from the database `DEFAULT now()` and is not re-read, so the POST
  response has no date. Entities set `createdAt` from the `Clock` in the
  constructor.
- **Validating a URL with a regular expression is fragile.** The first version
  accepted `javascript:1,alert(1)`. Use `@WebUrl` (parsing with
  `java.net.URI`) in the backend and `safeHref` in the frontend before building
  any `href`.
- **An integration test that logs in must use `TestSessions.login`.** The
  limiter is a bean in the shared context; logins all from 127.0.0.1 exhaust
  the per-network quota as the suite grows, and tests start failing with 429 in
  an unpredictable order.
- **`typescript-eslint` does not support TypeScript 7** (peer `<6.1.0` as of
  2026-11-05). Linting is `oxlint` (`npm run lint`, warnings as errors), which
  does not depend on the compiler. Do not force ESLint with
  `--legacy-peer-deps`.
- **nginx truncates bodies over 1 MB by default** (`client_max_body_size`),
  answering 413 before the backend sees them. The import accepts 2 MB (D14);
  the limit is in `frontend/nginx.conf`. A MockMvc upload test cannot catch
  this.
- **Spring Data ignores a repository declared as a nested interface**
  (`considerNestedRepositories = false`). One file per repository.
- **JSX drops trailing whitespace before a line break.** `{a}: ` on one line
  and `{b}` on the next renders `a:b`. Build the text in a template string.
- **The editor compiles into `backend/target` alongside Maven.** The VS
  Code/Eclipse Java server writes classes there; the symptom is a
  `NoClassDefFoundError` on a synthetic class (`Class$1`) or an "Unresolved
  compilation problem" in a test with nothing wrong with it. Run `mvn clean
  verify` before investigating.
- **HttpClient 5: closing the response drains the remaining body.** A server
  that sends more than the limit and keeps dripping bytes never hits the read
  timeout and holds the caller. `SafeHttpFetcher` cancels the request before
  closing. The test has to drip bytes slowly: with a stopped server the timeout
  ends the drain and the test passes even without the protection.
- **The database clock in a container differs from the JVM's.** On Windows the
  Docker VM can be seconds ahead. In a test, never compare SQL `now()` with an
  application instant; use an instant well in the past.
- **The JVM clock on Windows has up to ~16 ms resolution.** Two consecutive
  operations can receive the same `Instant`; ordering by `createdAt` with a
  UUID tiebreak then becomes random. In an ordering test, separate the instants
  through SQL rather than relying on the clock.
- **"Null type safety … unchecked conversion" from VS Code is a false
  positive** of Eclipse's null analysis on a method reference over a JDK
  generic. It is disabled in `.vscode/java-compiler.prefs` (local). Do not
  replace a method reference with a lambda because of it.
- **The API omits a null field from JSON**
  (`default-property-inclusion: non_null`). In a MockMvc test a null field is
  `jsonPath(...).doesNotExist()`, not `.value(nullValue())`; in the frontend
  the type is optional (`?:`), not `| null`.
- **The frontend's fake API matches routes by prefix.** `GET /leads/` also
  answers `/leads/{id}/anything` with the lead JSON. A new section in the lead
  detail must declare its route in both the `leads` and the `crm` tests.
- **nginx: `add_header` inside a `location` discards those from `server`.** The
  HTML page ended up with no security headers at all because of this. The
  headers live in `frontend/security-headers.conf`, included in **every**
  `location`; a new `location` needs the `include`. Check with `curl -I` on
  `/`.
- **A profile with no web server breaks `SecurityConfig`**
  (`web-application-type: none` → no `HttpSecurity`). The bootstrap starts with
  `server.port: 0` and exits through `BootstrapExit`.
- **`"\s"` in a Java string is a literal space** (Java 15+), not regex
  whitespace. Always write `"\\s"`, `"\\d"` inside a literal, including in SQL
  built in Java. One test passed by accident because the input contained only
  spaces.
- **`server.error.*` is no longer read on Boot 4** (deprecation level `error`):
  the keys are `spring.web.error.*`. A property with `error`-level deprecation
  is ignored silently, and the editor warning is the only clue.
- **PostgreSQL rounds, it does not truncate, fractions below the
  microsecond.** An `Instant` with nanoseconds written to `timestamptz` can
  come back *greater* than the original. Comparing a session instant with a
  stored one requires truncating to `MICROS` before writing and using the
  stored value on both sides.
- **`FOR UPDATE` + `REQUIRES_NEW` auditing = deadlock.** `audit_event` has a
  foreign key to `organization`; locking the organisation row and auditing in
  the same request makes the new transaction wait for the one that called it.
  Use `pg_advisory_xact_lock` to serialise.
- **`flyway_schema_history.version` is text**: `max(version)` returns `9` with
  V10 applied. Order by `installed_rank`.
- **Git Bash rewrites a path in a `docker run` argument** (`/mnt` becomes
  `C:/Program Files/Git/mnt`). Use `MSYS_NO_PATHCONV=1` and `$(pwd -W)`.
- **`curl -F file=@/c/...` in Git Bash does not translate the Windows path**:
  the request fails with no message. Run from inside the folder with a relative
  path.
- **The CSRF cookie needs `setCookiePath("/")`.** Without it, it inherits the
  `/api/v1` context path and the JavaScript on the page served at `/` cannot
  see it: login fails with 403 **in the browser** even though every API test
  passes. There is a regression test in `AuthControllerIntegrationTest`.
- **Cookies are not isolated by port.** Every service on `localhost` shares one
  cookie jar, and this machine runs other projects on 5173/8080/55433. A
  generic name (`XSRF-TOKEN`, `JSESSIONID`) collides, the browser sends **two
  cookies with the same name**, the server reads only the first and refuses
  with **403 and no useful message**. Hence `JH_XSRF` / `X-JH-XSRF` and
  `JHSESSION`. See D22.
- **Changing a cookie's path or name leaves an orphan in the browser.** The old
  one keeps being sent and may be read instead of the new one. When touching a
  cookie, test with the leftover present, not only with a clean jar.
- **The CSRF cookie is only reissued when the token is new.** An assertion
  about `Set-Cookie` on an HTTP request becomes order-dependent; test
  `CsrfTokenRepository` directly.
- **There is no `.prettierrc`; `npx prettier` with no options rewrites the
  frontend** with double quotes and 80 columns. The style is `--single-quote
  --print-width 120`, applied only to changed files.
- **Ports 55107–55806 are reserved by Windows/Hyper-V** on this machine
  (`netsh interface ipv4 show excludedportrange protocol=tcp`). Check before
  picking a new port.
- **`hibernate.order_inserts` reorders INSERTs by entity type** and can send a
  child row before its parent. The import confirm writes `company` and
  `import_row` in the same flush, and the foreign key violation surfaces as a
  **409** through `GlobalExceptionHandler`. `hibernate.jdbc.batch_size` is
  enabled and the reordering flag deliberately is not; `HibernatePropertiesTest`
  pins both.

## Ports (chosen to avoid collisions with other projects on this machine)

| Service | Port | Note |
|---|---|---|
| **Application (nginx)** | **8090** | **containerised entry point; this is where you test** |
| PostgreSQL | 15432 | 5432 is the local PostgreSQL; 55107–55806 are reserved by Windows |
| Backend | 8081 | 8080 belongs to the `agendamento` project; in containers, debugging only |
| Frontend (Vite dev) | 5174 | 5173 belongs to the `agendamento` project |

## Authentication

Session in the `JHSESSION` cookie (`HttpOnly`, `SameSite=Lax`, `Secure` via
`COOKIE_SECURE`). CSRF in a JavaScript-readable cookie, echoed in the header —
`httpClient` already does this for POST/PATCH/PUT/DELETE.

Public routes: `/health`, `/auth/login`, `/openapi/**`, `/docs/**`. Everything
else requires authentication, **including routes that do not exist**, which
answer 401 instead of 404 for anonymous callers. That is intentional: it does
not disclose which routes exist.

To sign in during development: set `LOCAL_ADMIN_PASSWORD` in `.env` and run
with the `local` profile (`SPRING_PROFILES_ACTIVE=local`). `LocalBootstrap`
creates the organisation and the administrator on first run (D19).

A resource from another organisation answers **404, never 403**: saying it
exists but belongs to another tenant is already a leak.

Membership is **revalidated in the database on every authenticated request**
(`MembershipRevalidationFilter`, D21). Revoking membership, disabling a user,
suspending an organisation or changing a role takes effect on the next request,
without waiting for the session to expire. Do not replace this with a cache
without re-reading D21.

The login limiter uses **atomic reservation** (`tryAcquire`, D20). Never
reintroduce an `isBlocked()`/`recordFailure()` pair: between the check and the
accounting, concurrent requests slip through. Eviction by cap **never removes a
blocked entry** — without that, flooding the limiter erases the attacker's own
block and returns unlimited attempts. There is also a per-IP limit, because the
caller chooses the e-mail.

**A limit test must be validated in reverse:** remove the protection and
confirm the test fails. Three versions of the limiter test passed without the
protection — one never reached the cap, one exhausted the IP cap first, and one
used a frozen clock, which gives every entry the same instant and makes
"oldest" meaningless.

## Pinned versions (checked against the registries on 2026-11-04)

Java 21 · Spring Boot 4.1.2 · SpringDoc 3.2.0 · Testcontainers 2.0.5 ·
PostgreSQL 18-alpine · React 19.3.1 · TypeScript 7.0.2 · Vite 8.3.5 ·
Vitest 5.1.0 · Tailwind 4.3.4.

Flyway, Jackson, Hibernate and the JDBC driver are managed by the Spring Boot
BOM — do not pin them by hand. When updating any version, check the registry
rather than assuming the latest.

Local connection: `postgresql://jhcomp@localhost:15432/jhcomp_prospect`. The
database listens on `127.0.0.1` only.

## Non-negotiable rules

These come from the specification and exist for legal or product reasons. Do
not work around or "simplify" any of them without a decision recorded in
`docs/11-decisions.md`.

1. **No automatic external sending.** The AI has no path to send, publish,
   remove a contact block or delete a record (`docs/06` §11). This is an
   architectural constraint: the AI layer is given no write tool. There is a
   structural test for it.
2. **`organization_id` on every private entity**, and the organisation context
   **always** comes from the authenticated session. Never accept
   `organizationId` from the client as a source of authorisation. Validate that
   related entities belong to the same organisation.
3. **"Not found" is not "does not exist".** `NOT_FOUND` never produces
   deficiency points in the score. Unknown information does not penalise.
4. **Do-not-contact is checked before generating, approving and recording** a
   message, and stays valid even if the lead is reclassified. An import never
   removes a block.
5. **`SENT` only with a confirmed send.** Opening a messaging app is not a
   send. Approved is not sent.
6. **A lead never moves to `CONTACTED` automatically** when a message is
   generated or approved. Only with operator confirmation.
7. **Secrets outside the code** (RNF-006). Never commit `.env`, a token or a
   key.
8. **The URL fetcher is protected against SSRF:** block private, loopback and
   metadata addresses; apply a timeout, a redirect limit and a response size
   limit (`docs/07` §6).
9. **AI output and external page content are untrusted data, never
   instructions.** Validate the schema, cap the size, never execute returned
   code.
10. **Timestamps in UTC** (RNF-009); money in `NUMERIC`, never `FLOAT`.
11. **Flyway migrations are immutable once applied.** Never edit an applied
    migration; create the next version.
12. **Test data is fictitious.** Never put real personal data or real company
    lists in fixtures or in the repository (`docs/10` §10).

## Code conventions

**Backend**

- `domain` depends on neither Spring, HTTP nor any external provider.
- A JPA entity is never exposed by the API. Request and response DTOs are
  explicit.
- A controller contains no business logic: it validates the request and calls
  the use case.
- Use a transaction in use cases that change several entities. **Never hold a
  transaction open during a network call.**
- Errors follow the format in `docs/05` §1. Never return a stack trace.
- External integration only through an adapter (`BusinessSource`,
  `AIProvider`), with a timeout and rate-limit handling.
- Every listing is paginated (RNF-002).

**Frontend**

- TypeScript `strict`. **`any` is forbidden**; avoid unjustified casts and `!`.
- One centralised HTTP client, never `fetch` scattered around.
- Every screen covers loading, empty, error, no permission and session expired
  (`docs/08` §11).
- Accessibility: keyboard navigation, visible focus, labels on fields, never
  colour alone for status, confirmation on destructive actions.
- **UI strings stay in Portuguese.**

**Avoid:** overengineering, abstraction with no concrete use, dependencies
without justification, duplication, oversized methods or components, comments
that restate the code, a provisional implementation presented as final.

## Definition of done

A stage is only complete with: the business rule implemented, input validation,
authorisation checked, error handling, adequate tests, updated documentation, a
security and privacy review where applicable, UI states covered, and logs free
of unnecessary sensitive data.

A negative cross-organisation access test is mandatory in every stage that adds
a private entity or endpoint.

## When finishing a stage

Report: what was implemented, files created or modified, tests run, the **real**
test results, open problems, and the next step. If a test failed, say that it
failed and show the output.
