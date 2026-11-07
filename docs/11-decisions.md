# 11 — Decision log

This document resolves the gaps and inconsistencies found while auditing the
specification (documents 01–10). It **complements** the others; where they
disagree, this one wins.

Every decision states the problem it closes, the decision taken, the reasoning
and the consequences. Several record a mistake and its correction, because a
decision log that only contains good news is not a log.

Status: **Approved** — decided and implemented · **Superseded** — replaced by
a later decision · **Void** — no longer applies.

| ID | Subject | Status | Stage |
|---|---|---|---|
| D1 | Authentication strategy | Approved | 3 |
| D2 | Lead state vs. deal stage | Approved | 7 |
| D3 | Where the score is computed and persisted | Approved | 6 |
| D4 | `Company.normalized_name` | Approved | 4 |
| D5 | `CompanyContact` out of P0 | Approved | 4 |
| D6 | `LeadSource.company_id` mandatory | Approved | 4 |
| D7 | Score baseline without an analysis | Approved | 6 |
| D8 | Ownership on `/findings/{id}/review` | Approved | 6 |
| D9 | Pipeline seeded by migration, no CRUD | **Superseded by D25** | 7 |
| D10 | Shared normalisation | Approved | 4 |
| D11 | `WebsiteTemplate` is a global entity | **Void (D13)** | — |
| D12 | Verification state of company data | Approved | 4 |
| D13 | Website Studio out of the MVP | Approved | post-MVP |
| D14 | Synchronous CSV import | Approved | 5 |
| D15 | Job table instead of `@Async` | Approved | 6 |
| D16 | Module granularity: 12 to 8 | Approved | 3 |
| D17 | Reduced end-to-end scope | Approved | 9 |
| D18 | `audit_event.ip_address` as `text` | Approved | 3 |
| D19 | First user by profile, not by migration | Approved | 3 |
| D20 | Login limiter: atomic reservation, bounded memory | Approved | 3 |
| D21 | Membership revalidated on every request | Approved | 3 |
| D22 | Project-specific cookie names | Approved | 3 |
| D23 | Two-phase CSV import | Approved | 5 |
| D24 | Score v1 criteria and website verification | Approved | 6 |
| D25 | Pipeline in code, deal without outcome, interactions | Approved | 7 |
| D26 | Reviewed AI outreach and do-not-contact list | Approved | 8 |
| D27 | Production on a VPS with Caddy, backup, monitoring | Approved | 9 |
| D28 | Member management and temporary passwords | Approved | 10 |
| D29 | Dashboard filters and conversion | Approved | — |
| D30 | Attention, funnel, owner and team on the dashboard | Approved | — |
| D31 | Lead list as a work queue | Approved | — |
| D32 | Deleting a mistaken company, merging, the list as a tool | Approved | — |

---

## D1 — Session authentication with an `HttpOnly` cookie · Approved

`docs/05` §2 left the choice between session and token open.

**Decision:** Spring Security with a session cookie — `HttpOnly`, `Secure`,
`SameSite=Lax` — plus a CSRF token. Frontend and backend served from the same
origin through a reverse proxy.

**Reason:** it removes refresh token rotation, revocation and blacklisting,
complexity that buys nothing with a single operator. Less code, smaller error
surface. A mobile app — the case where JWT would pay off — is out of scope.

**Consequences:** `/auth/refresh` leaves the API specification. CSRF becomes a
mandatory test requirement. The frontend stores no credential in
`localStorage`.

## D2 — `Lead.status` is the single source of truth · Approved

`docs/06` §3 allowed `Lead.status` and `Deal.stage` to exist as independent
states, requiring only that the difference be documented.

**Decision:** `Lead.status` is the only commercial state. Board columns map
one to one onto its values. Moving a card is `PATCH /leads/{leadId}/status`.
`Deal` exists only for the commercial facts — estimated value, service,
outcome, loss reason — and appears when the lead reaches `PROPOSAL_SENT`.

**Reason:** two independent states drift, and with a single operator no
reconciliation routine ever notices. This removes the entire class of
synchronisation bug.

**Consequences:** `PATCH /deals/{dealId}/stage` leaves the API. Several
simultaneous negotiations per company are unsupported; where needed, create
independent leads.

## D3 — A single derivation for the score · Approved

The score appeared in three places: `AnalysisFinding.points_awarded`,
`LeadAnalysis.score` and `Lead.score`.

**Decision:** one explicit chain. Findings sum into `LeadAnalysis.score`,
persisted with `score_version`; `Lead.score` is the score of the most recently
completed analysis. Reviewing a finding recomputes the whole chain
reproducibly.

## D4 — `normalized_name` is a persisted column · Approved

`docs/04` §2 proposed an index over `normalized_name`, but the column was not
in the field list.

**Decision:** persist it, filled by the component from D10. Computing it in
memory would make the index impossible, and deduplication depends on it. The
original name is always preserved.

## D5 — `CompanyContact` out of P0 · Approved

Phone and e-mail existed on `Company` **and** on `CompanyContact`, making
deduplication by phone and block checking ambiguous.

**Decision:** do not implement `CompanyContact` in the MVP. The fields on
`Company` are enough for the 30-company milestone. Reintroduce it when several
contacts per company are genuinely needed, at which point `Company` keeps the
primary contact and `CompanyContact` becomes the full list.

## D6 — `LeadSource.company_id` is mandatory · Approved

`docs/04` §2 made both `lead_id` and `company_id` nullable, which allows an
orphan provenance record.

**Decision:** `company_id` mandatory, `lead_id` nullable, enforced by a
constraint. Provenance describes where the company's data came from, and the
company outlives any individual opportunity.

## D7 — The score has a baseline without an analysis · Approved

Two criteria do not depend on a digital presence analysis: fit with the
services offered, and location served.

**Decision:** state explicitly that up to 20 points exist with no analysis at
all. The interface must never show "score 0" for an unanalysed lead as though
it were a negative assessment; it shows that no analysis exists.

## D8 — Ownership by chain on unscoped routes · Approved

`PATCH /findings/{findingId}/review` carries neither organisation nor lead in
the path.

**Decision:** validate the chain finding → analysis → lead → organisation
against the authenticated context. A negative cross-tenant test is mandatory.

## D9 — Pipeline seeded by migration, no CRUD · Superseded by D25

Proposed keeping `pipeline` and `pipeline_stage` tables seeded by Flyway, with
no CRUD and no admin screen.

**Superseded.** D25 removed the tables entirely. The "no CRUD" part stands.

## D10 — A single normalisation component · Approved

Phone normalisation appeared both as a deduplication rule and, separately, as
the normalised identifier of the do-not-contact list.

**Decision:** one component in `shared`, used by both, covering phone, domain,
URL and name.

**Reason:** if the two normalisations diverge, a do-not-contact request can be
bypassed by a difference in phone formatting. That is a compliance failure,
not a cosmetic defect.

## D11 — `WebsiteTemplate` is global · Void

Proposed marking the entity as global so the tenant filter would not reach it.
The entity was never created (D13), so the decision has nothing to apply to.
It is kept for numbering, and as a reminder that a decision can be made
obsolete by another one before it is ever implemented.

## D12 — Verification state lives in the analysis · Approved

RF-023 requires not silently overwriting information confirmed by another
source, but there was no verification state on `Company`.

**Decision:** do not add per-field verification to `Company`. Verification
stays in the analysis and in `LeadSource`; updates preserve a history of
relevant changes without marking field by field. Revisit if real use shows a
need.

## D13 — Website Studio out of the MVP · Approved

`docs/09` §9 placed the template catalogue and the site preview in Sprint 6.
RF-060 and RF-061 were already P1, not P0.

**Decision:** defer the module to after the MVP. The old Sprint 6 becomes
production hardening, end-to-end tests and real use with around 30 companies.

**Reason:** for the first companies, building a demonstration by hand is
faster than building a catalogue, a configuration schema and a preview engine
— and JH.COMP already builds sites as a service. It is also the module with
the highest legal risk (logos, photographs, unauthorised representation) and
the least validation, since nobody yet knows whether an unsolicited
demonstration raises conversion.

**Consequences:** RF-060, RF-061 and RF-062 leave the MVP. The three
`website_*` tables are not created.

## D14 — Synchronous CSV import in the MVP · Approved

`docs/05` §6 asked for asynchronous processing of large files, and RNF-004
requires asynchrony for slow operations.

**Decision:** synchronous import with a hard limit of 2,000 rows and 2 MB,
returning the report in the response itself. `GET /imports/{importId}` and
`/report` still exist and read the persisted record, so the API contract does
not change — only the execution mode.

**Reason:** the commercial milestone is about 30 companies. Building a queue,
polling and a status screen for that volume is complexity without return.
Moving to the job table (D15) later is a local change.

## D15 — A persistent job table instead of `@Async` · Approved

`docs/03` §7 left the choice open.

**Decision:** a job table with a `@Scheduled` polling worker. No Redis, no
RabbitMQ, no Kafka.

**Reason:** `@Async` loses work on restart. An analysis that calls an AI costs
money, and "average analysis cost per lead" is a product metric, so losing an
execution is a financial loss. Polling a table is simpler than any broker and
survives a restart.

## D16 — Module granularity: 12 to 8 · Approved

`docs/03` §3 defined 12 modules and §4 defined 4 layers with about 4
subpackages each — roughly 190 packages before the first business rule.

**Decision:** make simplification the rule.

- `enrichment` joins `company`: normalisation and refresh are operations on a
  company.
- `organization` joins `identity`: `OrganizationMember` links the two, and
  separating them forces a contract between inseparable parts.
- All four layers apply only to `company`, `lead`, `analysis` and `outreach`,
  which have real business rules.
- `discovery` and `dashboard` are flat. `dashboard` has no `domain` at all —
  it is read-only aggregate queries.

**Reason:** the biggest risk in this project is one developer drowning in
layers. The dependency boundaries of `docs/03` §5 still hold.

## D17 — Reduced end-to-end scope · Approved

`docs/10` §2 listed 12 end-to-end flows.

**Decision:** two. The first covers login, CSV import, qualification and a
pipeline move. The second covers generating a message, registering a
do-not-contact request and confirming that a new approach is blocked. The
other ten become API integration tests.

**Reason:** a large end-to-end suite maintained by one person becomes flaky
and then ignored. Risk coverage stays equivalent at a fraction of the
maintenance cost.

**Implementation.** Playwright (Chromium) against the full environment through
the nginx port, with the real CSP and real cookies. The flows also assert from
the server side that a blocked approach answers 409, and fail on any CSP
violation. Fictitious data with a unique suffix, because the database persists
between runs. Flow 12 of document 10 (site preview) does not exist (D13).

## D18 — `audit_event.ip_address` is `text`, not `inet` · Approved

V2 defined the column as `inet`. The JDBC driver sends the parameter as
`varchar` and PostgreSQL does not convert implicitly, so every insert failed
with "column is of type inet but expression is of type character varying".

**Decision:** `text` with `CHECK (length(ip_address) <= 45)`, which
accommodates IPv6. The format validation `inet` would give is not worth the
mapping friction.

**Note on immutability:** V2 was corrected in place rather than by a V3. The
immutable-migration rule applies from the moment a migration runs in a shared
environment; this one had only run against local and test databases, both
disposable, and nothing was committed. From here on, any change to V1 or V2
requires a new version.

## D19 — The first user is created by a profile, not a migration · Approved

`docs/09` §3 asked for "minimal seed data".

**Decision:** the initial organisation and administrator are created by
`LocalBootstrap`, restricted to the `local` profile, with the password coming
from `LOCAL_ADMIN_PASSWORD` and no default. Without the variable, nothing is
created.

**Reason:** seeding a user through a Flyway migration would put a known
password into every environment that runs the migrations, production included
— and migrations are immutable, so the mistake could not simply be corrected
afterwards. A default password would be a publicly known credential.

**Consequence:** in production the first administrator is created by an
explicit operational step, not by the deployment.

## D20 — Login limiter with atomic reservation and bounded memory · Approved

The first version exposed `isBlocked()` and `recordFailure()` separately and
never removed entries from the map. A security review found two real problems,
both reachable through the public `/auth/login` route:

1. **Race condition (TOCTOU).** Between the check and the accounting,
   concurrent requests all passed the test before any of them recorded a
   failure. With parallelism an attacker obtained far more than the intended 5
   attempts.
2. **Unbounded memory.** The key includes the e-mail, which the caller
   chooses. Distinct e-mails created entries that were only removed on a
   successful login, allowing memory exhaustion.

**Decision:** the only entry point is `tryAcquire(email, ip)`, which checks the
limit and records the attempt inside the same `ConcurrentHashMap.compute`,
atomic per key. The attempt is charged up front; `recordSuccess` returns the
quota. The map is capped at 20,000 keys, evicting expired entries and, if
necessary, the oldest ones.

**Two limits, not one.** Per e-mail and IP (5 per window) and per IP (50 per
window). A credential limit alone is not enough: the e-mail is part of the key
and chosen by the caller, so a single origin could try a different e-mail every
time, never be stopped, and fill the tracking map as a bonus.

**A memory cap that opens no bypass.** On reaching the cap, expired entries go
first and then the oldest ones **that are not blocked**. A blocked entry is
never evicted: that would be a direct bypass — flood the limiter, erase your
own block, start over, unlimited attempts. With no evictable candidate, new
keys are refused.

**Spaced sweeping.** Above the threshold, expired-entry cleanup runs at most
every 10 seconds. Sweeping the map on every attempt would cost O(n) per
request precisely while under attack. Cap-driven eviction ignores the interval,
because a memory limit has to apply immediately.

**A correction to an earlier claim.** The first version of this decision said a
test guaranteed that flooding the limiter did not erase an active block. It did
not: the test flooded 10,100 keys while eviction only triggers at 20,000, so it
exercised nothing but expiry cleanup. Two further attempts to fix the test also
failed to exercise eviction — one exhausted the IP cap before the key cap, the
other used a frozen clock, which makes "oldest" undefined. The current version
was validated by removing the protection and confirming the test fails.

**Grouping by network.** The volume limit is per origin network, not per exact
address: in IPv6 an attacker usually holds an entire `/64` and would rotate
addresses every attempt, slipping past the limit and filling the tracking map.
IPv4 stays exact, because grouping by `/24` would hit legitimate users with no
relation to the attacker.

**Hierarchical grouping (revised 2026-11-05).** The previous version grouped
IPv6 only by `/48`. That stopped rotation but let one abusive subscriber lock
out every neighbour on a provider handing out `/64`s from the same `/48`. There
are now three limits, widest to narrowest:

| Level | Key | Quota per window |
|---|---|---|
| Site | IPv6 `/48` or exact IPv4 | 100 (`MAX_ATTEMPTS_PER_SITE`) |
| Subscriber | IPv6 `/64` or exact IPv4 | 50 (`MAX_ATTEMPTS_PER_NETWORK`) |
| Credential | e-mail + subscriber | 5 (`MAX_FAILURES`) |

When the subscriber level refuses, the site charge is **refunded**: without the
refund, an already blocked `/64` that keeps hammering would drain the shared
quota and lock out the neighbours anyway. Rotating `/64` still costs the site,
because each new `/64` is accepted at the subscriber level. A refusal at the
credential level is not refunded: the attempt happened and counts towards the
origin's volume.

**Saturation made expensive, not tolerated.** At the cap, attempts from unseen
keys are refused. Allowing them would be a bypass: the limit would stop
applying exactly when saturated. Refusing costs availability, and what makes
that cost acceptable is that saturation is unreachable for a single origin:

- controlling a whole `/48` yields 100 attempts per window, twice a normal
  origin — not 65,536 times that;
- each attempt creates at most one entry per map, so the subscriber and
  credential maps are bounded by `MAX_TRACKED_SITES × MAX_ATTEMPTS_PER_SITE`
  (100,000) and never saturate before the site map;
- reaching refusal therefore requires a thousand distinct sites (`/48` or
  IPv4) from different operators.

A consequence for the tests: only the site map reaches its cap by flooding, and
that is where the "eviction does not remove an active block" test runs. The
eviction code is the same in all three maps.

Saturation is logged at error level, once per window: without that, the
observed symptom would be "nobody can log in" with no apparent cause.

**Limit of this design.** Under a genuinely distributed attack, an in-memory
limiter is not the right defence; edge filtering is. This decision deliberately
protects the password over availability in that extreme, and records the choice
rather than hiding it.

**History of this decision.** Four security reviews found problems in sequence
in this one class: the race condition, unbounded memory, eviction of an active
block, bypass by IPv6 rotation, and finally the oscillation between refusing
and allowing at saturation. The oscillation resolved once it became clear that
the choice between failing closed and failing open was only painful because
saturating was cheap; making saturation expensive dissolves the dilemma.

**Known limit:** the counter is in memory and applies per instance. If the
backend is ever replicated, it needs shared storage.

## D21 — Membership revalidated on every request · Approved

A security review found stale authorisation: `organizationId` and `role` were
frozen in the session for 8 hours. Revoking membership, disabling a user,
suspending an organisation or demoting an administrator had no effect until the
session expired, contradicting the acceptance criterion of RF-003.

**Decision:** `MembershipRevalidationFilter` reloads the active membership on
every authenticated request, before authorisation is evaluated. An invalid
membership ends the session; a changed role replaces the principal and is
written back to the session.

**Cost:** one indexed query per authenticated request, irrelevant at MVP
volume. The alternative — revalidating every N minutes — leaves a window in
which a revoked privilege still works. If volume grows, this filter is where an
interval or a cache belongs.

## D22 — Project-specific cookie names · Approved

Login failed in the browser with 403 while every API test and every `curl`
check passed.

**Diagnosis:** cookies are not isolated by port. Every service on `localhost`
shares one cookie jar. The browser held a leftover `XSRF-TOKEN` issued before
an earlier path fix and started sending two cookies with the same name. The
server reads only the first, compared the header against the wrong value and
refused. Nothing appeared in the audit trail because CSRF refusal happens in
the filter, before the controller.

**Decision:** cookie `JH_XSRF` and header `X-JH-XSRF` instead of the defaults,
and `JHSESSION` for the session. The old leftover becomes irrelevant without
anyone having to clear their browser.

**Underlying reason:** this machine runs other projects on `localhost` (5173,
8080, 55433). A generic cookie name collides between them, and the symptom — a
403 with no useful message — is expensive to diagnose.

**Process lesson:** changing a cookie's name or path leaves an orphan in the
browser that keeps being sent. Test with the leftover present, not only with a
clean jar. A `curl` test does not reproduce the path and duplication rules a
browser applies.

## D23 — Two-phase CSV import · Approved

`docs/04` defined no import entity and `docs/05` §6 listed only upload, query
and report. The roadmap, however, asks to "review and persist valid records",
and RF-014 forbids discarding a record silently.

**Decision:** import in two phases, with two new tables (V5).

1. `POST /imports/csv` **analyses**: reads the file, recognises the headers (or
   applies the mapping sent), validates each row with the same rules as manual
   entry, and classifies duplicates against the database and within the file
   itself. Writes `import_batch` and one `import_row` per record. **It creates
   no company.**
2. `POST /imports/{id}/confirm` **imports** the valid rows and the duplicates
   the operator chose, optionally opening one lead per company. Before writing,
   it reclassifies the valid rows against the database: a company registered
   between analysis and confirmation becomes a skipped row, visible in the
   report, rather than a silent duplicate.
3. `POST /imports/{id}/discard` gives up and erases the row values (data
   minimisation), keeping the totals.

**Rules:**

- D14 limits: 2 MB, 2,000 rows, 50 columns. A file over the limit is refused
  whole; never imported halfway.
- UTF-8 required. Decoding in the wrong charset does not fail — it replaces
  accents with rubbish, silently. Delimiter `;`, `,` or tab, detected from the
  header.
- Columns that match no company field are ignored and listed in the response;
  they are not stored, and neither is the raw file line.
- Confirmation runs in one transaction with an atomic status transition
  (`ANALYZED` → `IMPORTING`): two simultaneous confirmations cannot import
  twice, and a failure rolls everything back.
- Provenance per company: type `CSV_IMPORT`, the name the operator gave,
  reference "Import {id}, row N", and the analysis date as the collection date.
- Re-sending the same file is identified by the SHA-256 of its content and
  flagged (`previousImportId`); rows already imported appear as duplicates.
- An import only creates records, never changes existing ones, so it has no way
  to remove a contact block.

**Completing an existing company (revised 2026-11-05, V7).** A row marked as a
duplicate of an already registered company may, at the operator's choice,
complete that company instead of creating another:

- only empty fields are filled; no existing value is overwritten (RF-023). Name,
  city and state never change, because they identify the company;
- the `CSV_IMPORT` provenance is recorded even when no field was filled,
  because the row also confirms the company (RF-022);
- the report shows which fields were filled (`MERGED`, `mergedFields`);
- a company archived after the analysis is not changed: the row becomes
  `SKIPPED`;
- when leads are being created, the company gets one only if it has no open
  opportunity. An open opportunity, `DO_NOT_CONTACT` included, is not
  duplicated and does not fail the import;
- a duplicate of another row in the same file cannot complete anything: there
  is no registered company to complete.

## D24 — Score v1 criteria and website verification · Approved

`docs/06` §7 defined point groups ("verifiable technical problem, up to 20")
but not how each point is earned — and automated analysis needs rules that
never assert without evidence.

**Criteria.** Each criterion is a statement worth its full points only when
`CONFIRMED`; any other state is worth zero.

| Criterion | Points | Automatic verification |
|---|---:|---|
| `SITE_ABSENT` — the company has no site | 25 | Never confirms. With no site on file, or a site that is down: `NOT_VERIFIED`. Only the operator confirms absence. |
| `NO_HTTPS` | 10 | HTTPS connection to the host. |
| `NO_MOBILE_VIEWPORT` | 10 | Home page HTML; medium confidence. |
| `NO_CONTACT_LINK` — no `tel:`, `mailto:` or WhatsApp link | 10 | Home page HTML; medium confidence. |
| `NO_ONLINE_BOOKING_OR_CATALOG` | 10 | None. Always `NOT_VERIFIED`; a text heuristic would be inferring without evidence. |
| `BUSINESS_INFO_SUFFICIENT` | 15 | The company record: segment and a contact channel. |
| `SERVICE_FIT` | 10 | `jhcomp.analysis.served-segments`; empty leaves it for review. |
| `LOCATION_SERVED` | 10 | `jhcomp.analysis.served-states`, default `MG`. |

**Score.** `ScoreFormula` v1 is a pure function of the states. The analysis
stores the score and the version; the lead takes the score of the most recently
completed analysis (resolving D3). The site-independent criteria (D7) are
computed inside each analysis, which is equivalent to "baseline added" while
keeping a single source. A lead with no analysis has no score, and the
interface says so rather than showing zero.

**Review.** The operator chooses the finding state; the original verification
result is kept (`original_status`), the note becomes `OPERATOR_NOTE` evidence,
and the score is recomputed. Ownership of `/findings/{id}/review` is checked
through the organisation of the finding, with composite keys guaranteeing the
chain (resolving D8).

**Execution.** The request answers `202` and enqueues a job in the same
transaction (D15): `job` table, `@Scheduled` worker with `FOR UPDATE SKIP
LOCKED`, three attempts with increasing backoff, a job locked for more than 10
minutes returns to the queue. One active analysis per lead; asking again
returns the same one. A `DO_NOT_CONTACT` lead is not analysed: analysis
prepares an approach, and processing the data of someone who asked not to be
contacted serves no purpose.

**Website verification.**

- Only `http`/`https` on ports 80 and 443; no cookies, authentication or proxy.
- The address is validated in the `DnsResolver` used by the connection itself
  (Apache HttpClient 5), which closes DNS rebinding. It blocks loopback,
  private ranges, link-local (including the metadata address
  169.254.169.254), CGNAT, IPv6 local, reserved ranges and IPv4 embedded in
  IPv6.
- No automatic redirects; up to 3, each revalidated, with the new host's
  robots.txt respected.
- Connection and read timeouts, plus a total 15 s budget per request enforced
  by a timer that cancels the request, which covers headers sent one byte at a
  time. 45 s for the whole verification. Headers capped at 100 lines of 8 KB;
  body at 1 MB. The request is cancelled before closing: closing without
  cancelling drains the rest of the body, and a server dripping bytes would
  hold the worker.
- IPv6 by allowlist: global unicast (2000::/3) only, excluding 6to4, Teredo,
  ORCHID and documentation ranges; forms carrying IPv4 are refused.
- robots.txt respected (RFC 9309): 4xx allows, 5xx or no response forbids.
  Wildcards are matched without regular expressions — third-party rules with
  several `*` would cause ReDoS — capped at 500 rules of 512 characters.
- Only two boolean signals and the SHA-256 of the content leave the page. The
  HTML is neither stored nor interpreted as an instruction.

**Out of scope:** AI, navigation beyond the home page, and automatic detection
of booking or catalogue.

## D25 — Pipeline in code, deal without an outcome, interactions · Approved

**Pipeline stages in code, no table.** D9 proposed `pipeline` and
`pipeline_stage` tables seeded by Flyway. Since the stages are 1:1 with
`Lead.status` (D2) and have no CRUD, a table would be a second source of truth
to keep in sync with the enum, and an organisation created after the migration
would have no pipeline at all. The stages live in `PipelineStages`; `GET
/pipeline-stages` exposes them. `DO_NOT_CONTACT` is not a stage: it is a block,
outside the commercial flow. When the pipeline needs to be configurable, it
gets a table.

**Deal without an outcome.** The deal holds service, value (`numeric(12,2)`,
BRL) and loss reason. Won or lost is the lead's status: storing the outcome on
the deal would recreate the two sources of state that D2 eliminated. The API
response carries an `outcome` derived from the lead. The deal appears on
reaching `PROPOSAL_SENT`, or `WON` without a proposal, through an event
published in the same transaction as the status change.

**Interactions.** Recording an interaction never changes the lead status: a
recorded contact does not become `CONTACTED` on its own. The exception is a
do-not-contact request, which has to take effect immediately and moves the lead
to `DO_NOT_CONTACT` where the state allows. An outbound approach to a blocked
lead is refused; whatever the company does on its own initiative is still
recordable.

## D26 — Reviewed AI outreach and do-not-contact list · Approved

**Provider.** `AIProvider` has a single method, `generateMessage`; the
`analyzeLead` of `docs/03` is dropped, because the analysis is deterministic
(D24). The MVP has only `FakeAIProvider`, deterministic and also used by the
tests; a real provider comes later behind configuration, with the key outside
the code, without changing the flow. The AI package depends on no repository,
no writing service and not on `outreach`, and a structural test enforces it.

**Context sent.** Name, segment, city and state, and the `CONFIRMED` findings
of the latest completed analysis, plus the sender (organisation and operator)
and the channel. Phone, e-mail, Instagram and operator notes are **not** sent
to the provider; external page content never enters the prompt. The prompt is
versioned in a resource (`outreach-v1`), and the message records
`prompt_version` and `generated_by`.

**Output.** The structure `{subject?, body, unverifiedClaims[]}`, validated:
non-empty body; per-channel limits (WhatsApp and Instagram 1,000, e-mail 4,000,
call script 2,000 characters); a subject only for e-mail. Invalid output is
refused whole, with no draft stored, and the text is treated as plain text.

**Synchronous execution.** `POST /leads/{leadId}/messages/generate` reads the
context in a short transaction, calls the provider **outside** any transaction
and stores the draft in another, checking the block again. A 20 s timeout is
the responsibility of whichever adapter makes the network call; the fake makes
none. Generation is limited to 10 per user every 5 minutes, with atomic
reservation as in D20. An unavailable provider or refused output answers 502
`AI_PROVIDER_ERROR` with nothing stored.

**States.** A generated message starts as `DRAFT`, or `NEEDS_REVIEW` when the
output carries `unverifiedClaims`; approving then requires explicit operator
confirmation in the request. Editing an `APPROVED` message returns it to
`DRAFT` and clears the approval. A failed send does not become an interaction.
`record-send` only starts from `APPROVED`. `SENT`, `FAILED` and `CANCELLED` are
final. The lead moves to `CONTACTED` only when `record-send` explicitly asks
for it, never by default.

**Blocking.** `SuppressionEntry` stores the normalised identifier, the channel
(null means all), the reason, `requested_at` and who recorded it; creation is
audited without the identifier. Before generating, approving and recording a
send, and when recording an outbound approach in the CRM, the company's
identifiers are checked against the list, and a lead in `DO_NOT_CONTACT` blocks
as well. The check is conservative: any identifier of the company blocked on
all channels blocks the company on all; an entry restricted to one channel
blocks only that one. The CRM queries through the `ContactBlockCheck`
interface, implemented in outreach, to avoid a circular dependency. Because the
block is by identifier, a company re-imported with the same contact stays
blocked. Any member can record one; only `ADMIN` can list them. There is no
removal in the MVP.

## D27 — Production on a VPS with Caddy, backup and monitoring · Approved

**Topology.** A `docker-compose.prod.yml` separate from the development one
rather than an override: an override would inherit the debug ports (8081,
15432). Only Caddy publishes ports (80 and 443) and handles TLS and HSTS. The
database, API and nginx stay on the internal network. `COMPOSE_FILE` in the
server's `.env` makes `docker compose` use the production file without `-f`.

**`prod` profile.** Session cookie `Secure` fixed, API documentation off, and
`server.forward-headers-strategy: native`. Without the last one, every request
would appear to come from the nginx container: the per-IP login limit (D20)
would apply to all users at once, and any one of them could lock out everyone.
Tomcat accepts `X-Forwarded-For` only from an internal proxy, and Caddy
replaces whatever the client sends; the rehearsal confirmed that a forged
`X-Forwarded-For` never reaches the audit trail.

**First administrator (closing the consequence of D19).** A single-use command:
the `bootstrap` profile (`scripts/create-admin.sh`) starts the API on an
unpublished random port, creates the organisation and the administrator, and
exits. It refuses to run when an organisation exists, requires a 12-character
password and fails with a non-zero exit code rather than merely logging a
warning. The password arrives through an inherited environment variable, never
on the command line.

**Headers.** CSP restricted to the same origin (the Vite build has no inline
script or style), `frame-ancestors 'none'`, `nosniff`, `Referrer-Policy`,
`X-Robots-Tag`. The headers live in a file included in **every** nginx
`location`: `add_header` inside a `location` discards those inherited from
`server`, which is why the HTML page had none of them before this stage.

**Health.** `/api/v1/health` checks the database connection and answers 503
`DOWN` without it. An "UP" with the database down would fool both the container
health check and the external monitor.

**Backup.** `pg_dump` in custom format, daily by cron, checked with `pg_restore
--list` before being accepted, with SHA-256 and 14-day retention.
`verify-backup.sh` restores into a disposable PostgreSQL and compares counts
with the live database; `restore.sh` takes a safety backup before erasing. A
copy off the server is an operational responsibility, documented in
`docs/12-operations.md`; the script does not encrypt the dump.

**Monitoring.** An external monitor on the public health endpoint — the only
alert that works when the whole server is down — and `healthcheck.sh` on cron:
containers, health over HTTPS, backup age and disk, with an optional webhook.

## D28 — Member management and temporary passwords · Approved

RF-003 asked for roles and for administrative operations to be blocked; roles
and revalidation existed (D21), but there was no way to register, deactivate or
reset anyone, nor to change one's own password. In production only the initial
administrator could get in.

**Registration without invitations.** There is no e-mail channel in the MVP.
The administrator enters name, e-mail and role; the server generates a
20-character temporary password (unambiguous alphabet, ~113 bits), returned in
that response only and stored only as a hash. An e-mail already registered **in
any organisation** is refused: attaching an existing account would let one
administrator set the password of someone who also belongs to another
organisation. The 409 reveals that the e-mail exists, which is accepted because
only administrators reach the endpoint.

**Mandatory change.** With a temporary password, the session reaches only
`/auth/me`, `/auth/logout` and `/auth/password`; everything else answers 403
`PASSWORD_CHANGE_REQUIRED` through the authorisation rule, not merely through
the screen. The change requires the current password even when it is temporary
— holding the session alone must not be enough to take over the account — and
goes through the login limiter (D20).

**Password policy.** At least 12 characters and at most 72 UTF-8 bytes, the
BCrypt limit. Length, with no composition rule (NIST SP 800-63B).

**Sessions.** `credentials_changed_at` records the last change or reset;
revalidation (D21) ends sessions opened before it. Changing your own password
drops your other sessions and keeps the current one, which receives the same
instant; an administrator reset drops all of the member's sessions. The instant
is truncated to microseconds before being stored: PostgreSQL rounds the smaller
fraction, and the stored value could land after the instant of the session that
changed the password, ending precisely that one.

**Role and access.** The administrator changes roles and revokes or restores
access for other members, effective on the next request (D21). Nobody changes
their own membership or resets their own password through this route, which
prevents an organisation from accidentally losing its last administrator; the
active-administrator count is checked anyway. A PostgreSQL advisory lock per
organisation serialises concurrent changes. It is **not** `SELECT ... FOR
UPDATE` on the organisation: auditing writes in its own transaction with a
foreign key to the organisation, and would wait forever for the lock held by
the transaction that called it.

**Auditing.** `MEMBER_CREATE`, `MEMBER_UPDATE`, `PASSWORD_CHANGE` (success and
failure) and `PASSWORD_RESET`, with no e-mail or password in the detail, and
written after the rules so a refused change never appears as done.

**Out of scope:** e-mail invitations, self-service password recovery, MFA,
disabling the user account globally, and deleting a member.

## D29 — Dashboard filters and conversion · Approved

**Origin.** The type (`MANUAL`, `CSV_IMPORT`, `INTEGRATION`) of the company's
**first** recorded provenance: the path by which it entered the system. An
import that later completes the company (D23) records new provenance but does
not change the origin, so a company never counts in two groups. The lead
listing still filters by "has some provenance of this type" — a different
question.

**Conversion.** Cohort: leads opened in the period, within the filters. Per
group: leads (the denominator), contacted (with a recorded outbound approach)
and won. Segment is free text, so only the 10 largest groups are shown; an
empty segment forms the "no segment" group.

**Minimum sample: 10 leads.** Below that, a single lead moves the rate by more
than 10 points. The server marks `sufficientSample`; the screen shows counts
only, with no percentage, and flags the small sample. The minimum is returned
in the response.

**Time series.** The four period metrics in day, week (Monday to Sunday) or
calendar-month intervals in the Brasília time zone. Every interval appears,
with zero when empty. The first week or month starts at the beginning of the
period, not before. The screen shows one metric at a time: a value in reais and
a count never share an axis. Daily granularity only up to 92 days.

## D30 — Attention, funnel, owner and team on the dashboard · Approved

The dashboard showed snapshots and totals: it said neither what to do nor where
the process loses leads.

**Needs attention.** The situation now, with no period: open tasks overdue or
due today (Brasília day); leads idle for 7 days or more in an in-progress stage
(the same threshold as the Kanban); proposals untouched for 7 days; in-progress
leads with no open task. `NEW` is triage and stays out of the last two. A lead
in `DO_NOT_CONTACT` stays out of all of them: the panel never pushes an
approach to someone who asked not to be contacted.

**Conversion funnel.** Cohort: leads opened in the period (as in D29). Reach of
a stage: how many got to it or beyond, by history; won counts as having passed
through all, lost and blocked advance nobody. The screen shows the step from
one stage to the next only when the previous one has the minimum sample of 10.
Time in stage: the average over those who have left it; those still in it are
excluded. The average needs 10 exits or more.

**Owner.** `assignee` filters every dashboard route by the lead's owner. An id
from another organisation simply returns zero, because every query is already
scoped by the session.

**Team.** `GET /team` returns only the id and display name of active members.
E-mail and role are not needed to assign a lead — the minimum necessary.

**Drill-down.** The lead list reads and writes its filters in the URL, so the
dashboard can take a number or a stage to the filtered list. The period does
not travel with it: the list shows who is in the stage now.

## D31 — Lead list as a work queue · Approved

The lead list showed status and score; the detail spread the lead's state over
some ten cards. Neither said what to do with the lead.

**Work queue.** Read in SQL in the CRM module: owner, deal value, next open
task (nearest due date, no due date last) and stage entry, sortable by any of
them with nulls last. It does not live in the lead module's listing because
those fields belong to the CRM, and the lead module does not depend on it.
One-click filters follow the Kanban and attention rules (D30). The origin is
the first provenance (D29), as on the dashboard, so a list opened by drill-down
matches the number that opened it.

**Segment.** Free text on both sides meant every entry invented its own
spelling while the filter demanded an exact match. The filter is now a list of
the segments in use, with counts and a "no segment" option; comparison ignores
case and surrounding spaces across leads, companies and the dashboard; the form
suggests existing segments. On save, a segment loses surrounding and repeated
spaces and adopts the spelling that already exists in the organisation; a new
segment is kept as typed. V13 unified the already stored spellings once, by the
most used one.

**Bulk actions.** Owner and priority only, up to 100 leads, in one transaction:
an id from another organisation answers 404 and nothing changes. Status is
excluded: `CONTACTED` requires confirming the contact lead by lead, and the
final states deserve individual confirmation.

**Saved views.** Name and the list's query string. Private: only the owner sees
and deletes it, not even an administrator (up to 20 per person). Shared: an
administrator publishes it for the team, everyone sees it, only an
administrator deletes it (up to 20 per organisation). Names are unique
case-insensitively. The query accepts URL characters only (up to 500), and the
screen applies only the filters it knows.

**Summary and detail.** Clicking a lead opens a summary beside the list without
leaving it. The detail puts the essentials and the four everyday actions at the
top, properties, deal and tasks in a fixed sidebar, history and recording
together, and analysis and messages in tabs. Previous/next follow the worked
page of the list, and the shortcuts `n`, `t`, `j`, `k` work outside text
fields.

## D32 — Deleting a mistaken company, merging, and the list as a tool · Approved

A company could only be archived (RF-010). Archiving preserves history but
leaves in the database whatever was entered by mistake: a duplicate typed
twice, a wrong spreadsheet imported whole.

**Real deletion, only for a "clean" company.** Clean means no commercial
activity: at most the lead opened with it, still `NEW` and carrying only the
opening status change, with no interaction, task, deal, message or analysis.
With any history, the path remains archiving (`409`). Who may: whoever
registered it, within 7 days, or an administrator at any time. The screen asks
for the typed name and offers the button only when the server would accept.
It erases the company, the open lead, its status history and the provenance;
the import row that referenced the company stays in the batch as `REMOVED`,
without the link (V14). **Contact blocks are never touched** — they live on the
identifier, not on the company. The audit record keeps only the id.

**Undoing an import.** The same rule applied to the companies the batch
created: the clean ones go, those with activity stay, and those the batch
merely completed (`MERGED`) are untouched, because they existed before it.
Whoever imported, within 7 days of completion, or an administrator; once only
(`import_batch.undone_at`, V14). The screen shows the preview before
confirming.

**Merging a duplicate.** Leads, provenance and import rows move from the source
company to the target; the target's empty fields are filled from the source
without overwriting (RF-023); the source is archived, never deleted. Two
companies with an open lead do not merge (one open opportunity per company):
the operator closes one cycle first. Any member can merge, as with archiving.

**Authorship and merging (V15).** Who registered a company is stored on the
company itself (`created_by_user_id`) and is no longer inferred from the first
provenance: merging moves provenance, and the author of the source would become
the one who "registered" the target. The source keeps
`merged_into_company_id`; being a merge target counts as activity and is never
deleted. The import row that created the source becomes `MERGED` when it moves
to the target, and undoing an import only deletes a company created by the
batch's author after the batch started: undoing the source's import never
erases the target, which existed before it.

**Out of scope:** deleting a company with history (needs its own decision,
covering retention and LGPD) and undoing a merge (the source stays archived for
reference, but the leads do not come back on their own).
