# 06 — Business rules

## 1. Concepts

- **Company** — the master record of a business.
- **Lead** — a commercial opportunity related to a company.
- **Source** — the origin of a piece of information or of a record.
- **Finding** — a result of an analysis.
- **Deal** — the commercial facts of an opportunity.
- **Interaction** — a relationship event on a lead.
- **Outreach** — preparing and recording an approach.

Company and Lead are not synonyms. A company can exist without an active
opportunity; a lead is a concrete intention to evaluate or offer a solution.

## 2. Lead lifecycle

States:

`NEW` · `QUALIFIED` · `CONTACT_APPROVED` · `CONTACTED` · `IN_CONVERSATION` ·
`PROPOSAL_SENT` · `WON` · `LOST` · `DO_NOT_CONTACT`

`Lead.status` is the **single source of commercial state** (D2). Permitted
transitions live in `LeadStatusTransitions`; anything else answers `409`.

Rules:

- `WON` and `LOST` are terminal for the opportunity. A new commercial cycle
  requires a new lead.
- `DO_NOT_CONTACT` blocks further outreach covered by the request **even if
  the lead is reclassified**.
- Relevant changes record the actor, the date and the previous and new state.
- The system never moves a lead to `CONTACTED` when a message is generated or
  approved.
- `CONTACTED` is recorded only after the operator confirms the contact
  happened — the API requires `contactConfirmed: true` for that transition.

## 3. Pipeline and deal

The board columns are, in order: New, Qualified, Contact approved, Contacted,
In conversation, Proposal sent, Won, Lost.

There is no separate `Deal.stage`. The original design had both `Lead.status`
and a deal stage, which is two sources for one fact and a guaranteed drift;
D2 settled it: the columns map one to one onto `Lead.status`, moving a card is
`PATCH /leads/{leadId}/status`, and the deal holds only value, service,
outcome and loss reason.

One active opportunity per company. Where several sales per company are
eventually needed, create independent leads rather than duplicating the
company.

## 4. Deduplication

Normalisation, performed by a single shared component (D10) — the same one the
do-not-contact list uses, because two implementations would mean a phone
blocked in one place and reachable in the other:

- collapse excess whitespace;
- normalise case for comparison;
- normalise phone numbers to E.164 where possible;
- normalise domain and URL;
- **always preserve the original values.**

Matching signals, strongest first: a reliable, legitimately collected business
identifier; the website domain; the business phone; the normalised name
together with city and state.

Rules:

- an exact match on a reliable identifier is a strong candidate;
- name similarity is only a suggestion;
- **never merge automatically on a similar name alone**;
- sources and history are preserved through a merge;
- the operation is audited (`COMPANY_MERGE`).

Merging moves leads, provenance and import rows to the surviving company,
fills only its empty fields, and archives the source. Two open leads answer
`409` (D32).

## 5. Provenance and data quality

Everything obtained from an external source records the source, a reference or
URL where permitted, the collection date, the responsible user or integration,
and a verification state.

States: confirmed · not found · not verified · not applicable · contradicted.

**"Not found" means only that the search performed did not locate the
information. It is not proof of absence**, and it never produces deficiency
points in the score.

Data beyond its freshness window is marked potentially out of date, per a
policy set by information type.

## 6. Digital presence analysis

The analysis observes signals such as: whether a website exists and is
reachable, HTTPS, an observable viewport, contact links, basic service
information, and the presence of a catalogue or booking where applicable.

It does not infer commercial quality, revenue, purchase intent or operational
problems without evidence.

Each finding carries a criterion, a state, a description, evidence, a
confidence level, the points awarded, and the human review.

Automated analysis respects robots.txt, terms of use, technical limits and
applicable law. It never works around authentication, blocks or access
controls. All fetching goes through `SafeHttpFetcher`, which blocks private,
loopback and metadata addresses, and applies a timeout, a redirect limit and a
response size limit (D24).

## 7. Lead score

Deterministic, maximum 100:

| Criterion | Points |
|---|---:|
| Absence of a website, confirmed by an adequate method | 25 |
| Verifiable technical problem on the site | up to 20 |
| Observable, relevant functional need | up to 20 |
| Business information sufficient for evaluation | 15 |
| Fit with the services offered | 10 |
| Location served | 10 |

In the implementation (`Criterion`): `SITE_ABSENT` 25; `NO_HTTPS`,
`NO_MOBILE_VIEWPORT`, `NO_CONTACT_LINK` 10 each; `NO_ONLINE_BOOKING_OR_CATALOG`
10; `BUSINESS_INFO_SUFFICIENT` 15; `SERVICE_FIT` 10; `LOCATION_SERVED` 10.

Rules (D24):

- **A criterion scores only when `CONFIRMED`**, and nothing else.
- Unknown information never earns deficiency points.
- A non-applicable criterion never penalises.
- The absence of a website is never confirmed automatically — only by human
  review. An automated check that fails to reach a site has found nothing, not
  proven absence.
- `NO_ONLINE_BOOKING_OR_CATALOG` has no automatic heuristic: inferring it from
  page text would be asserting without evidence.
- The score records the formula version, and the breakdown is shown.
- The operator may correct findings, which recalculates the score.
- The score is not a purchase probability and not a guarantee of conversion.
- No sensitive attribute and no personal inference ever enters the score.

Priority bands: 0–29 low, 30–59 medium, 60–79 high, 80–100 very high. These
must be calibrated against real outcomes and must never be presented as an
objective assessment of a business. Until roughly fifty closed leads exist,
treat them as a hypothesis rather than a measurement.

## 8. Messages and approval

- AI-generated messages start as `DRAFT` or `NEEDS_REVIEW`.
- The AI may use only the data and findings present in the context.
- It must not invent a prior relationship, a personal observation or any fact
  about the company.
- It must not claim the operator visited, tested or analysed something they
  did not.
- Every message identifies the sender and the reason for contact.
- The operator reviews both the content and the recipient.
- **No automatic external sending.** Approved is not sent.
- An outcome is recorded only on operator confirmation.

Statements the generated text makes that no confirmed finding supports are
listed in `unverifiedClaims`, and approving such a message requires
`unverifiedClaimsReviewed: true`.

## 9. Do not contact

- A request is recorded promptly.
- It blocks further outreach on the applicable identifier and channel.
- The block is checked **before generating, before approving and before
  recording** a contact.
- A block is never removed without an authorised process and a documented
  justification. There is no removal path in the MVP (D26).
- An import never clears a block.
- Only the minimum data needed to honour the restriction is kept.

## 10. Website Studio — out of scope

Removed from the MVP by decision D13. The rules that governed it (a preview is
a demonstration and not an official site; no third-party logos or photographs
without a licence; never publish on a domain associated with the business
without authorisation; never present a demonstration as commissioned or
approved by the establishment) remain the position of record should the
feature ever return.

## 11. AI — functional limits

The AI **may** summarise findings, suggest opportunities and draft messages.

The AI **may not** send messages, change permissions, decide a legal basis,
invent evidence, publish anything, execute generated code, delete records or
remove contact blocks.

This is an architectural constraint, not a policy: the AI layer is given no
write tool, so there is no code path from a model's output to any of those
actions. A structural test enforces it. Outputs are validated and reviewable,
and are treated as untrusted input — schema checked, size capped, never
executed.
