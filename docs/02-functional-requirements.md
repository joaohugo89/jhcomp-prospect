# 02 — Functional requirements

## 1. Conventions

Priorities:

- **P0:** indispensable for the MVP.
- **P1:** important, may land after the main flow.
- **P2:** future evolution.

Every requirement is implemented with input validation, authorisation and
relevant tests. A requirement is not done until a negative cross-organisation
test exists for the entity or endpoint it introduces.

Each requirement below carries its implementation status: **implemented**,
**partial** or **not implemented**, so the document describes the system as it
is rather than as it was planned.

## 2. Identity and organisation

### RF-001 — Authentication (P0) — implemented

Users authenticate with credentials.

**Acceptance:** valid credentials grant access; invalid ones are rejected
without revealing whether the e-mail exists.

Session cookie, no JWT and no refresh token (D1). The password comparison runs
even when the user does not exist, so response time does not disclose which
addresses are registered.

### RF-002 — Organisation (P0) — implemented

Every authenticated user operates inside one active organisation.

**Acceptance:** the organisation is resolved from the backend security context;
the client cannot select another organisation. Membership is revalidated
against the database on every authenticated request (D21), so revoking access,
disabling a user, suspending an organisation or changing a role takes effect on
the next request rather than when the session expires.

### RF-003 — Members and roles (P1) — implemented

Members hold administrative or operational roles.

**Acceptance:** administrative operations are refused for operators. The admin
registers a member and receives a temporary password once; until it is changed
the session can only reach `/auth/me`, `/auth/logout` and `/auth/password`
(D28). Changing or resetting a password ends that user's other sessions. Nobody
can change their own membership.

## 3. Companies and leads

### RF-010 — Company record (P0) — implemented

Create, read, update and archive companies. Fields: name, trade name, segment,
city, state, website, social profiles, business phone, business address where
relevant, and notes.

**Acceptance:** required fields are validated and the data belongs to the
organisation.

A company is deleted only when it has no commercial activity — at most the
`NEW` lead opened with it — by whoever registered it, within 7 days, or by an
admin; otherwise it is archived (D32).

### RF-011 — Lead record (P0) — implemented

Create a commercial opportunity attached to a company.

**Acceptance:** a lead has a status, an origin and a creation date.
`Lead.status` is the single source of commercial state (D2).

### RF-012 — Listing and filters (P0) — implemented

Search and filter by name, segment, city, status, priority, score and origin.

**Acceptance:** paginated listing, permitted sort orders, combinable filters.
The lead list is a work queue (`/leads/worklist`), with bulk changes limited to
owner and priority, and private or shared saved views — only an admin publishes
or deletes a shared one (D31).

### RF-013 — History (P0) — implemented

Show relevant changes and interactions for a lead.

**Acceptance:** the history is chronological and cannot be altered by users
without permission.

### RF-014 — Deduplication (P0) — implemented

Detect possible duplicate companies from the available identifiers.

**Acceptance:** the system proposes matches and allows review before merging;
it never discards records silently. Merging moves leads and sources, archives
the origin, and refuses with `409` when both sides have an open lead (D32).

### RF-015 — CSV import (P0) — implemented

Import a CSV with column mapping, validation and a report.

**Acceptance:** show rows read, imported, rejected and possibly duplicate;
per-row errors are inspectable.

Two phases (D23): analysing creates nothing; confirming imports the valid rows
and the chosen duplicates, and fills only the **empty** fields of an existing
company. Limits: 2 MB and 2,000 rows (D14).

## 4. Discovery and sources

### RF-020 — Manual research (P0) — implemented

Register companies found by the operator.

**Acceptance:** origin, source URL or reference, collection date and
responsible user are recorded when available.

### RF-021 — Integrated source search (P1) — not implemented

Run searches against an external source whose API and terms have been
assessed.

**Acceptance:** the integration is isolated behind an adapter; provider
failures and rate limits are surfaced clearly.

No provider has been selected, so no adapter exists. Leads currently enter by
CSV or by hand. The `BusinessSource` port is specified in document 03 and is
the intended seam.

### RF-022 — Provenance (P0) — implemented

Every imported or enriched value records where it came from and when.

**Acceptance:** the user can tell the origin of a value and when it was last
updated.

### RF-023 — Data refresh (P1) — partial

Update data from a new query or a manual review.

**Acceptance:** keep a minimal history of relevant changes and never silently
overwrite information confirmed by another source.

Manual review is implemented; scheduled refresh depends on RF-021.

## 5. Analysis and qualification

### RF-030 — Request an analysis (P0) — implemented

Request a digital presence analysis for a company.

**Acceptance:** the analysis has a status (pending, running, completed, failed)
and a date. It runs as a background job (D15) and the lead takes the score of
the most recently completed analysis (D3).

### RF-031 — Findings with evidence (P0) — implemented

Each finding carries a description, verification state, evidence or reference,
confidence and date.

**Acceptance:** unverified findings are never presented as confirmed facts.

### RF-032 — Explainable score (P0) — implemented

A deterministic score from 0 to 100 over configured criteria.

**Acceptance:** criteria and the points awarded are shown; missing information
is never automatically a deficiency.

A criterion scores only when `CONFIRMED`, and the absence of a website is never
confirmed automatically — only by human review (D24). Website checks go through
`SafeHttpFetcher` alone, with SSRF protection, timeouts, size limits and
robots.txt.

### RF-033 — Human review (P0) — implemented

Correct, confirm or reject findings.

**Acceptance:** the review records the user and the date.

## 6. CRM

### RF-040 — Pipeline (P0) — implemented

Show leads on a Kanban board.

**Acceptance:** moving a lead between stages respects valid transitions and
permissions. Columns are `PipelineStage` rows whose `stage_key` maps one-to-one
to `Lead.status`; moving a card is `PATCH /leads/{leadId}/status` (D2).

### RF-041 — Interactions (P0) — implemented

Record contacts, replies, meetings, proposals, do-not-contact requests and
other interactions.

**Acceptance:** each interaction records channel, date, outcome and notes.

### RF-042 — Tasks (P1) — implemented

Create tasks attached to leads.

**Acceptance:** a task has a title, a due date, a status and an optional owner.

### RF-043 — Won / lost (P0) — implemented

Close an opportunity as won or lost.

**Acceptance:** a lost deal may record a reason; a won deal may record the
value and the contracted service. `Deal` holds only value, service, outcome and
loss reason — the state itself lives in `Lead.status` (D2).

## 7. Outreach

### RF-050 — Generate a message (P0) — implemented

Draft a contextual message from the data and the approved findings.

**Acceptance:** the message is saved as a draft; the AI cannot send anything.

The AI returns text only (`AIProvider.generateMessage`, with no write tool —
there is a structural test for it). Generation is synchronous and holds no
transaction open during the call, and the output is validated before it becomes
a draft (D26).

### RF-051 — Review a message (P0) — implemented

Edit, approve or discard a draft.

**Acceptance:** only an approved message can be marked ready for external use.
Approved is not sent.

### RF-052 — Record a send (P0) — implemented

Record a send performed manually or through an enabled integration.

**Acceptance:** record channel, date, responsible user and outcome; never claim
a send that was not confirmed. Opening a messaging app is not a send, and a
lead never moves to `CONTACTED` automatically.

### RF-053 — Do not contact (P0) — implemented

Record a do-not-contact request and block further commercial outreach.

**Acceptance:** the block is checked before generating, approving and recording
a message, by normalised identifier, and stays valid even if the lead is
reclassified. An import never clears a block. There is no removal in the MVP
(D26).

### RF-054 — Channel integration (P2) — not implemented

Integrate channels through official, authorised APIs.

**Acceptance:** respect provider requirements, channel policies and privacy
controls.

## 8. Website Studio — removed from scope

Requirements RF-060, RF-061 and RF-062 (templates, preview, contracted
project) were **removed from the MVP by decision D13**. No `WebsiteTemplate`,
`WebsiteProject` or `WebsiteDeployment` entity exists, and none is to be
created. The section is kept as a numbered placeholder so the requirement
identifiers in the rest of the documentation keep their meaning.

## 9. Dashboard

### RF-070 — Summary (P0) — implemented

Show lead counts per stage, recent activity and deals.

**Acceptance:** indicators respect the organisation scope and the period
filter. A company's origin is its first provenance record (D29). Attention
signals are uniform across the board, the list and the dashboard: idle for 7
days, proposal with no contact for 7, no interaction for 14; a lead in
`DO_NOT_CONTACT` never appears (D30).

### RF-071 — Conversion (P1) — implemented

Show rates by origin and segment when the sample is large enough.

**Acceptance:** denominators and period are explicit; no misleading percentages
on thin data. The minimum sample is 10 (D29).

## 10. Related non-functional requirements

- **RNF-001:** every commercial operation respects organisation isolation.
- **RNF-002:** listings are paginated.
- **RNF-003:** errors share one format and never expose stack traces.
- **RNF-004:** slow external operations run asynchronously.
- **RNF-005:** external integrations have timeouts and failure handling.
- **RNF-006:** secrets are never stored in source code.
- **RNF-007:** logs avoid personal content and credentials.
- **RNF-008:** critical actions produce an audit trail.
- **RNF-009:** timestamps are persisted in UTC and displayed in the configured
  time zone; money is `NUMERIC`, never floating point.
- **RNF-010:** the frontend is responsive and keyboard-accessible for the main
  operations.
