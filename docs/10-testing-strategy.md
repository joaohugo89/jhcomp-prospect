# 10 — Testing strategy

## 1. Goals

Guarantee the integrity of the commercial rules, isolation between
organisations, import quality, data traceability, the safety of the outreach
flow, consistency of analyses, stability of integrations, and usability of the
main flow.

Current state: **440 backend tests**, **169 frontend tests**, **2 end-to-end
journeys**.

## 2. The pyramid

### Unit — JUnit 5 and Mockito

Covering company normalisation, deduplication, status transitions, score
calculation, do-not-contact blocking, message validation, pipeline rules and
authorisation policies in services.

Unit tests are fast and touch neither the network nor any external service.

### Integration — Spring Boot Test and Testcontainers with PostgreSQL

Covering repositories, Flyway migrations, constraints, transactions,
per-organisation filters, imports, paginated queries, endpoints and
serialisation, and history persistence.

A real PostgreSQL in a container, never H2: the behaviour that matters here —
composite foreign keys, `inet`, `timestamptz` rounding, advisory locks — is
exactly the behaviour H2 does not reproduce.

Two rules this suite learned the hard way:

- **The container must be a singleton**, started in a static block, without
  `@Testcontainers`/`@Container`. With that extension the container stops at
  the end of each class while Spring reuses the cached context, and the
  following classes fail with "Failed to obtain JDBC Connection".
- **An integration test that logs in must use `TestSessions.login`.** The
  limiter is a bean in the shared context; logins all coming from 127.0.0.1
  exhaust the per-network quota as the suite grows, and tests start failing
  with 429 in an unpredictable order.

### Frontend — Vitest and React Testing Library

Covering forms, validation, filters, empty and error states, score components,
the pipeline, message review and basic accessibility.

The simulated API matches routes **by prefix**: `GET /leads/` also answers
`/leads/{id}/anything` with the lead JSON. A new section in the lead detail
therefore has to declare its route in both the `leads` and the `crm` tests.

### End-to-end — Playwright

Two journeys (D17), chosen because they are the two that would cost real money
if they broke:

1. import a company from CSV, qualify the lead and move it on the pipeline;
2. generate and approve a message, and confirm that a do-not-contact request
   blocks a new approach.

They run against the containerised environment and assert that no
Content-Security-Policy violation appears in the console, because the header
is the production one and a violation would break a screen without any unit
test noticing.

The remaining journeys from the original plan — login, register a company,
create a lead, review a duplicate, analyse a lead, record an interaction — are
covered at integration level instead. The website preview journey went with
D13.

## 3. Critical isolation tests

Two test organisations, A and B. Verify that user A cannot list B's companies,
cannot reach B's lead by UUID, cannot change B's deal, cannot read B's
messages; that imports and jobs keep the right scope; that filters and the
dashboard never mix data; and that a relation between entities of different
organisations is rejected.

Assert both the HTTP response and the effect in the database. **A negative
cross-organisation test is mandatory for every step that adds a private
entity or endpoint.**

## 4. Deduplication tests

Same domain and name · phone in different formats · name with accents and
extra spaces · same name in different cities · similar name but distinct
companies · record with neither site nor phone · the same file imported twice.

Criteria: strong duplicates are flagged, ambiguous cases require review, and
nothing is merged silently on textual similarity alone.

## 5. Score tests

Minimum 0, maximum 100, criteria summed correctly, unknown never scoring as a
deficiency, non-applicable handled by rule, formula version persisted, changes
to findings recalculating reproducibly, and the breakdown shown.

## 6. Outreach and privacy tests

A generated message starts as a draft · the AI has no send path, which is
asserted structurally rather than behaviourally · a message requires review ·
approved is not sent · a send is recorded only after confirmation · a
do-not-contact request blocks further contact · an import never removes a
block · the block is checked before approving and before recording · logs do
not expose unnecessary sensitive content.

## 7. Integration tests

Stub timeout, unavailability, rate limit, invalid response, partial data,
duplicated response, authentication failure and exceeded quota.

Never run automated tests against production without authorisation and
defined limits.

The `SafeHttpFetcher` test needs the server to drip bytes slowly: with a
stopped server the timeout ends the drain and the test passes **even without
the protection**, which is precisely the false positive this document exists
to prevent.

## 8. Security

Input validation · per-resource authorisation · CSRF · XSS in rendered
content · SQL injection · SSRF in URL checks · file upload · rate limiting ·
secret exposure · CSV export and formula injection · vulnerable dependencies.

**Every test of a limit or a block must be validated in reverse:** remove the
protection and confirm the test fails. This is not a formality. Three versions
of the login limiter test passed with the protection removed — one never
reached the cap, one exhausted the IP cap first, and one used a frozen clock,
which gives every entry the same instant and makes "oldest" meaningless.

## 9. CI

Format and lint · compile · unit tests · integration tests · frontend build ·
frontend tests · dependency analysis · image build where applicable. A merge
is blocked when a required test fails.

One local trap: the IDE compiles into `backend/target` alongside Maven. The
symptom is a `NoClassDefFoundError` on a synthetic class (`Class$1`) or an
"Unresolved compilation problem" in a test with nothing wrong with it. Run
`mvn clean verify` before investigating.

## 10. Test data

- **Fictitious data only.** Never real personal data in a fixture, and never a
  real list of companies in the repository.
- No production credentials in CI.
- Mask data in non-production environments.
- Data created by an end-to-end run carries a unique suffix, because the
  environment's database persists between runs and a repeated name or phone
  would become a duplicate or a block left over from an earlier run.

One consequence worth stating: **the end-to-end suite writes to the database
of the environment it runs against.** Pointing it at an environment that holds
real prospecting data will mix test companies into the real pipeline. It
deserves its own environment once real data exists.
