# 09 — MVP roadmap

## 1. Strategy

Build in usable increments. The system had to start supporting the commercial
operation before the advanced modules were finished.

The original estimate was 6 sprints, roughly 6–8 weeks of consistent
individual work. It was a planning estimate, never a commitment.

## 2. Priorities

### P0 — required for the MVP — **all delivered**

Authentication · organisation · companies and leads · CSV import ·
deduplication · provenance · analysis with evidence · explainable score ·
pipeline · message drafting and review · interaction recording ·
do-not-contact list · dashboard.

### P1 — extended MVP

| Item | Status |
|---|---|
| Tasks | delivered |
| Additional filters and reports | delivered (work queues, saved views, conversion report) |
| First integration with an authorised source | **not started** — no provider selected |
| Templates and simple preview | **cancelled** by D13 |

### P2 — future

Official messaging integrations · site publication · visual editor · SaaS
billing · advanced automation · mobile app.

## 3. What was built, by stage

| Stage | Delivered | Acceptance |
|---|---|---|
| 0 — Setup | Repository, conventions, CI, pinned versions, Docker Compose | Frontend and backend start locally; PostgreSQL reachable; CI builds and tests |
| 1 — Foundation | Modular structure, initial migrations, `Organization`/`User`/`OrganizationMember`, authentication, authorisation, organisation context, global error handling | An authenticated user reaches only their own organisation; negative isolation tests pass |
| 2 — Companies and leads | CRUD, paginated listings, filters, CSV import, import report, assisted deduplication, provenance | A test file imports, duplicates are identified, valid records persist, origin is queryable |
| 3 — Intelligence | Analysis model, findings and evidence, verification states, deterministic score, finding review, background jobs | An analysis shows evidence, state, confidence and score version; unknown information is not a confirmed deficiency |
| 4 — CRM | Default pipeline, Kanban, deals, interactions, tasks, dashboard | An operator follows a lead from start to close; changes and interactions appear in the history |
| 5 — AI and outreach | `AIProvider` port, fake provider, versioned prompts, draft generation, output validation, editing and approval, manual send recording, suppression list | The AI drafts from available context only; the operator reviews; a block prevents an approach; nothing is sent automatically |
| 6 — Launch | End-to-end tests of the main flows, documentation, deployment, backup and restore verified | Production environment protected and monitored |

Stage 6 originally also carried the Website Studio. It was cut by D13 rather
than delivered, and the stage closed on operations instead.

## 4. First commercial milestone — **not yet done**

Validate with roughly 30 real companies from the region, obtained from
permitted sources.

For each record: source and date · segment and location · confirmed data ·
observed opportunity · operator review · decision to approach or not ·
outcome of the interaction.

Then assess data quality, research time, usefulness of the score, relevance of
the messages, replies and conversations started, and which segments fit best.

**Do not treat the number of messages sent as a measure of success.**

This milestone is the real remaining work. Everything above it is software;
this is the part that tells whether the software was worth building, and it
is the input that should decide the backlog below rather than the other way
round.

## 5. Definition of done

A story is finished only with: business rules implemented · input validation ·
authorisation · error handling · adequate tests · updated documentation ·
security and privacy review where applicable · loading, error and empty states
in the interface · logs free of unnecessary sensitive data.

A negative cross-organisation access test is mandatory in every stage that
adds a private entity or endpoint.

## 6. Post-MVP backlog

In the order their value is currently understood — which the milestone in
section 4 is expected to change:

1. **A real AI provider** behind the existing `AIProvider` port. The seam is
   built and the fake stays as the test provider.
2. **Less friction in recording a send.** The operator copies the text,
   switches app, sends, comes back and marks it. Fewer steps without breaking
   the rule that `SENT` requires a confirmed send.
3. **One data entry source**, even semi-automatic, so that companies enter on
   a cadence rather than whenever someone remembers.
4. Better filters and segmentation.
5. Scheduled data refresh.
6. Deeper technical analysis of sites.
7. Commercial proposals.
8. Official e-mail/WhatsApp integration, after a requirements and privacy
   review.
9. External organisations and SaaS plans.

Items 4 to 9 are candidates, not commitments. Nine items in a backlog written
before contact with real data is nine guesses.
