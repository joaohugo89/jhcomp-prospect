# 05 — API specification

This document describes the endpoints that exist. The live contract is served
by OpenAPI at `/api/v1/openapi`, with Swagger UI at `/api/v1/docs` — both
disabled in the `prod` profile.

## 1. Conventions

- Base path: `/api/v1`.
- JSON, UTF-8.
- UUIDs for identifiers, ISO 8601 for dates, UTC for instants.
- Pagination with `page`, `size` and `sort`; every listing is paginated
  (RNF-002) and `size` is capped at 100.
- One error format everywhere.
- Session authentication by cookie (D1).
- The organisation is resolved from the authenticated context and is **never**
  taken from the request body.
- A null field is omitted from the response (`default-property-inclusion:
  non_null`). In a MockMvc test that means `jsonPath(...).doesNotExist()`, not
  `.value(nullValue())`; in the frontend the type is optional (`?:`), not
  `| null`.

Error shape:

```json
{
  "type": "VALIDATION_ERROR",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid",
  "instance": "/api/v1/companies",
  "errors": [
    { "field": "name", "message": "must not be blank" }
  ]
}
```

A resource belonging to another organisation answers **404, never 403**.
Routes that do not exist also answer 401 for anonymous callers rather than
404, so the API does not disclose which routes exist.

## 2. Authentication

| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/login` | Authenticate |
| POST | `/auth/logout` | End the session |
| GET | `/auth/me` | Current user and active organisation |
| POST | `/auth/password` | Change your own password (D28) |

There is no `/auth/refresh` and no JWT: the session is a cookie (`JHSESSION`,
`HttpOnly`, `SameSite=Lax`, `Secure` in production), with a CSRF token in a
JavaScript-readable cookie (`JH_XSRF`) echoed in the `X-JH-XSRF` header (D1,
D22).

While a temporary password is pending, the session reaches only `/auth/me`,
`/auth/logout` and `/auth/password`; everything else answers `403
PASSWORD_CHANGE_REQUIRED`. A wrong current password answers `400
INVALID_CREDENTIALS`, not 401, because the session is still valid.

Member management (D28), `ADMIN` only:

| Method | Endpoint | Description |
|---|---|---|
| GET | `/organizations/members?includeRevoked=&status=&search=` | List members. `status` (`ACTIVE`, `REVOKED`) overrides `includeRevoked`; `search` matches name or e-mail. Each item carries `passwordChangeRequired` and `lastLoginAt`, taken from the audit trail |
| GET | `/organizations/members/summary` | `active`, `admins`, `revoked`, `awaitingFirstAccess` |
| GET | `/organizations/members/{memberId}` | Member detail |
| POST | `/organizations/members` | Register. The response carries the temporary password, once and only once |
| PATCH | `/organizations/members/{memberId}` | Change `role` and/or `status` |
| POST | `/organizations/members/{memberId}/reset-password` | Issue a new temporary password |

| Method | Endpoint | Description |
|---|---|---|
| GET | `/team` | Active members, `userId` and `displayName` only — enough to assign a lead and filter by owner. E-mail and role stay in `/organizations/members`, which is admin-only (D30) |

## 3. Dashboard

| Method | Endpoint | Description |
|---|---|---|
| GET | `/dashboard/summary` | Headline indicators |
| GET | `/dashboard/activity` | Recent activity, paginated |
| GET | `/dashboard/conversion` | Conversion by origin and segment (RF-071) |
| GET | `/dashboard/timeline` | Period metrics by day, week or month |
| GET | `/dashboard/attention` | What needs action now (D30) |
| GET | `/dashboard/funnel` | Reach per stage and average time in it (D30) |

Optional parameters, identical on every route: `from`, `to`, `segment`,
`city`, `source` (`MANUAL`, `CSV_IMPORT`, `INTEGRATION` — the company's origin,
D29) and `assignee` (the lead owner's id, D30).

- **`summary`** returns `leadsByStatus` (a snapshot of now), `activeLeads`,
  `companiesAdded`, `contactsRegistered`, `openProposals`, `dealsWon`,
  `wonValue`, `openTasks`, `overdueTasks` and `companiesWithoutSegment`,
  together with the effective period (`from`/`to`, Brasília dates, last 30 days
  by default). The per-stage count lives here; there is no separate
  `/dashboard/pipeline` route.
- **`conversion`** (D29) returns, for leads opened in the period, `bySource`
  and `bySegment` (up to 10 segments) with `leads`, `contacted`, `won` and
  `sufficientSample`, plus `minimumSample`. Below the minimum the screen shows
  no percentage at all.
- **`timeline`** (D29) also accepts `granularity` (`DAY`, `WEEK`, `MONTH`;
  without it, up to 31 days is daily, up to 120 weekly, beyond that monthly)
  and returns `points` with `start`, `companiesAdded`, `contactsRegistered`,
  `dealsWon` and `wonValue` for **every** interval in the period, including the
  empty ones.
- **`attention`** (D30; `limit` 1–20, default 5) returns `tasks`,
  `staleLeads`, `quietProposals` and `withoutTask`, each with `total` and
  `items` (`leadId`, `companyName`, `status`, `at`, `detail`, `overdue`). It is
  the situation now, with no period.
- **`funnel`** returns `cohort`, `minimumSample` and `stages` (`status`,
  `reached`, `exits`, `avgDays`, `sufficientSample`).

## 4. Companies

| Method | Endpoint | Description |
|---|---|---|
| POST | `/companies` | Create |
| GET | `/companies` | List and filter |
| GET | `/companies/{companyId}` | Detail |
| PATCH | `/companies/{companyId}` | Update |
| POST | `/companies/{companyId}/archive` | Archive |
| DELETE | `/companies/{companyId}` | Delete a company with no activity (D32) |
| GET | `/companies/{companyId}/removal` | Whether deletion would be accepted right now (D32) |
| GET | `/companies/{companyId}/sources` | Provenance history (RF-022), paginated |
| GET | `/companies/{companyId}/duplicates` | Possible duplicates (RF-014) |
| GET | `/companies/{companyId}/summary` | One work-queue row, for previews and the detail header |
| GET | `/companies/{companyId}/leads` | Commercial cycles of the company, paginated |
| GET | `/companies/worklist` | The list as a working tool (D32) |
| GET | `/companies/segments` | Segments in use, with company counts and how many have none |
| POST | `/companies/duplicate-check` | Possible duplicates while the form is being typed |
| POST | `/companies/bulk` | Segment or city/state on up to 100 companies |
| POST | `/companies/{companyId}/merge` | Merge a duplicate into this company (D32) |

Creation example:

```json
{
  "name": "Empresa Exemplo",
  "tradeName": "Exemplo",
  "segment": "BARBERSHOP",
  "city": "Poços de Caldas",
  "state": "MG",
  "source": {
    "name": "Map search",
    "url": "https://exemplo.test/local",
    "collectedAt": "2026-11-05T12:00:00Z"
  }
}
```

- **Provenance.** `source` is optional; without it the system records "manual
  entry" with the current date. On this route the type is always `MANUAL`: the
  client cannot choose the type, so hand-typed data can never be recorded as an
  import. A `collectedAt` in the future is rejected.
- **URLs** (`websiteUrl`, `instagramUrl`, `source.url`) accept `http`, `https`
  or no scheme. Any other scheme (`javascript:`, `data:`, …) is rejected with
  `VALIDATION_ERROR`, because the URL becomes a link in the interface.
  Validation parses with `java.net.URI` rather than matching a regular
  expression — the first regex-based version accepted
  `javascript:1,alert(1)`.
- **Partial PATCH.** An absent field keeps its value; an empty string clears an
  optional one. `name`, `city` and `state` cannot be cleared. An archived
  company answers `409 CONFLICT`.
- **Listing.** Filters `search` (name, accent- and case-insensitive),
  `segment`, `city`, `state`, `includeArchived` (default `false`). Sorting
  `sort=field,asc|desc` over `name`, `city`, `createdAt`, `updatedAt`; any
  other field answers `400`.
- **Duplicates.** Signals `DOMAIN`, `PHONE` and `NAME_CITY_STATE` (identical
  normalised name in the same city and state), at most ten per signal, always
  within the organisation. It is a suggestion for review: nothing is merged.
- The response never exposes `organizationId` or the normalised columns.

Company tooling (D32):

- **Work queue** (`/companies/worklist`). Filters `search`, `segment` or
  `noSegment=true`, `city`, `source`, and `focus`: `WITHOUT_LEAD`,
  `WITHOUT_CONTACT` (no phone, e-mail, website or Instagram), `DUPLICATE`
  (another **active** company with the same domain, phone, or name in the same
  city and state) and `ARCHIVED`; without `ARCHIVED`, only active ones. `sort`
  is `NAME`, `CITY`, `CREATED` (default) or `LAST_ACTIVITY`, `direction` is
  `asc|desc`, nulls last. Each item carries `currentLead` (the open one, or the
  most recent with `open: false`), `hasPhone`/`hasEmail`/`hasWebsite`/
  `hasInstagram`, `origin`, `leadCount`, `lastActivityAt` and
  `possibleDuplicate`.
- **Duplicate while typing** is a `POST` so the phone and the website never
  reach the URL: `{name, city, state, websiteUrl, phone, excludeCompanyId}`,
  all optional. A name only counts together with city and state.
- **Bulk:** `{"companyIds": [...], "segment": "..."}`, or `"city"` and
  `"state"` together. The segment is stored in the spelling that already
  exists (D31). Archived companies are untouched and come back in
  `skippedArchived`; an id from another organisation answers `404` and nothing
  changes.
- **Merge:** `{"sourceCompanyId": "..."}`. Leads, provenance and import rows
  move to the company in the path; its empty fields are filled from the source
  without overwriting anything; the source is archived. Response `{company,
  filledFields, movedLeads}`. Same company: `400`. Archived target, or both
  sides with an open lead: `409`. Audited as `COMPANY_MERGE`.
- **Delete:** body `{"confirmationName": "..."}`, the company name, compared
  without case or surrounding whitespace, otherwise `400`. With commercial
  activity: `409`. Without permission — not the creator, or past the 7-day
  window, and not an admin: `403`. Success: `204`; the company, the open lead
  with it and the provenance are erased, and the audit record
  (`COMPANY_DELETE`) keeps only the id. `/removal` answers `{allowed, blocker:
  HAS_ACTIVITY|NOT_ALLOWED, allowedUntil}`.

## 5. Leads

| Method | Endpoint | Description |
|---|---|---|
| POST | `/leads` | Create |
| GET | `/leads` | List and filter |
| GET | `/leads/{leadId}` | Detail |
| PATCH | `/leads/{leadId}` | Update the permitted fields |
| PATCH | `/leads/{leadId}/status` | Change status |
| PATCH | `/leads/{leadId}/priority` | Change priority |
| GET | `/leads/{leadId}/timeline` | Consolidated history |
| GET | `/leads/worklist` | Work queue: owner, value, next task, time in stage (D31) |
| GET | `/leads/{leadId}/summary` | The same fields as one work-queue row |
| POST | `/leads/bulk` | Owner or priority on up to 100 leads, all or nothing (D31) |
| GET | `/leads/views` | Own views plus those shared with the team (`shared`, `canDelete`) |
| POST | `/leads/views` | Save a view (`name`, `query`, `shared`; only an admin shares) |
| DELETE | `/leads/views/{viewId}` | Delete an own view; a shared one, admin only |

`/leads/worklist` accepts `search`, `status`, `priority`, `city`, `segment`
(case-insensitive), `noSegment`, `source`, `assignee`, `unassigned`,
`scoreMin`, `scoreMax`, `focus` (`OVERDUE`, `STALE`, `WITHOUT_TASK`), `sort`
(`COMPANY`, `SCORE`, `CREATED`, `UPDATED`, `STAGE`, `VALUE`, `NEXT_TASK`),
`direction`, `page` and `size`. Nulls always sort last.

- **Creation:** `{"companyId": "...", "priority": "HIGH", "source": {...}}`.
  An absent `priority` becomes `MEDIUM`; `source` follows the company rule. The
  lead starts as `NEW`, with the first history event. A company from another
  organisation answers `404`; an archived one, or one with an open
  opportunity, `409`.
- **Status:** `{"status": "QUALIFIED", "reason": "...", "contactConfirmed":
  false}`. A transition outside `LeadStatusTransitions` answers `409`. Moving
  to `CONTACTED` requires `contactConfirmed: true` (document 06 §2); without
  it, `400`. Re-sending the current status produces no event. `WON` and `LOST`
  fill `closedAt`.
- **PATCH `/leads/{id}`:** `{"assignedUserId": "..."}`; `null` clears the
  owner. A user who is not an active member answers `404`.
- **Response:** includes a summarised `company` and `allowedTransitions`,
  which guides the interface while the rule itself stays on the server. An
  absent `score` means no analysis, never zero (D7).
- **Timeline:** `STATUS_CHANGE` and `SOURCE` events, newest first, paginated
  in a single order.
- **Listing:** `scoreMin`/`scoreMax` exclude leads with no score;
  `scoreMin > scoreMax` answers `400`.

## 6. Import

| Method | Endpoint | Description |
|---|---|---|
| POST | `/imports/csv` | Upload and analyse a CSV. Creates nothing |
| GET | `/imports` | List imports, paginated; `status` filter |
| GET | `/imports/summary` | `pendingReview`, `completed`, `undone`, `discarded`, `companiesImported` |
| GET | `/imports/{importId}` | Status and totals |
| GET | `/imports/{importId}/report` | Per-row result, paginated, `status` filter |
| POST | `/imports/{importId}/confirm` | Import the accepted rows |
| POST | `/imports/{importId}/discard` | Give up without importing |
| GET | `/imports/{importId}/undo` | Undo preview: what goes and what stays |
| POST | `/imports/{importId}/undo` | Undo a completed import (D32) |

- **Upload:** `multipart/form-data` with `file` (`.csv` or `.txt`, UTF-8, up to
  2 MB and 2,000 rows), optional `sourceName` and `datasetNote`, and an
  optional `mapping`, a JSON object `{"name": "Column", "city": "Column", …}`
  keyed by company field. Without `mapping`, headers are recognised by alias
  (`Razão Social`, `Município`, `UF`, `Telefone`, …). `name`, `city` and
  `state` need a column; otherwise `400`.
  nginx caps the request body at 1 MB by default and would answer 413 before
  the backend sees it, so `frontend/nginx.conf` raises the limit — a MockMvc
  upload test cannot catch that.
- **Analysis response:** `201` with status `ANALYZED`, totals (`totalRows =
  validRows + rejectedRows + duplicateRows`), `columnMapping`,
  `ignoredColumns`, and `previousImportId` when the same file was sent before.
- **Report row:** `rowNumber` (the header is row 1), `status` (`VALID`,
  `REJECTED`, `DUPLICATE`, `IMPORTED`, `MERGED`, `SKIPPED`, and `REMOVED` once
  the company has been deleted, D32), `values`, `errors` in the API's field
  error format, and for duplicates `duplicateOfCompanyId` or
  `duplicateOfRowNumber` with `duplicateSignals`.
- **Confirmation:** `{"includeDuplicateRows": [7], "mergeDuplicateRows": [6],
  "createLeads": true}`. `includeDuplicateRows` imports the duplicate as a new
  company; `mergeDuplicateRows` fills the empty fields of the existing company
  — only for a duplicate of a registered company — without overwriting
  anything. A row that is not a possible duplicate, a `merge` of a duplicate of
  another row in the file, or the same row in both lists answers `400`.
  Confirming or discarding an import that is not `ANALYZED` answers `409`.
- **Undo** (D32): only `COMPLETED`, and only once (`409` otherwise). It deletes
  the companies the import created that still have no activity; those with
  activity stay (`keptWithActivity`) and those it merely completed are
  untouched (`mergedUntouched`). Whoever imported, within 7 days of
  completion, or an admin; otherwise `403`. The import gains `undoneAt`.
  Audited as `IMPORT_UNDO`.
- Do not execute spreadsheet formulas; protect exports against CSV injection.
- An import from another organisation answers `404` on every route.

## 7. Analyses

| Method | Endpoint | Description |
|---|---|---|
| POST | `/leads/{leadId}/analyses` | Request an analysis |
| GET | `/leads/{leadId}/analyses` | List analyses |
| GET | `/analyses/{analysisId}` | Analysis detail |
| PATCH | `/findings/{findingId}/review` | Review a finding |

- **Request:** answers `202` with the analysis `PENDING`. If one is already
  `PENDING` or `PROCESSING`, that one is returned. A `DO_NOT_CONTACT` lead
  answers `409`; a lead from another organisation, `404`.
- **Status:** `PENDING`, `PROCESSING`, `COMPLETED` or `FAILED`, with
  `createdAt`, `startedAt`, `completedAt` and a summarised `failureReason`.
  `score` and `scoreVersion` exist only once completed.
- **Detail:** `analysis` plus `findings`, one per criterion, with `status`,
  `originalStatus`, `description`, `confidence`, `pointsAwarded`, `maxPoints`,
  the review, and `evidence` (type, reference URL, description, date, author,
  content hash).
- **Review:** `{"status": "CONFIRMED", "note": "..."}`, answering the
  recalculated detail. An unfinished analysis answers `409`; a finding from
  another organisation, `404`.

## 8. CRM

| Method | Endpoint | Description |
|---|---|---|
| GET | `/pipeline-stages` | List stages (a code constant, 1:1 with `Lead.status`) |
| GET | `/pipeline/board` | Kanban board: up to 50 cards and the total per stage |
| GET | `/leads/{leadId}/deal` | Read the deal (404 before the proposal) |
| PUT | `/leads/{leadId}/deal` | Update service, value and loss reason |
| POST | `/leads/{leadId}/interactions` | Record an interaction |
| GET | `/leads/{leadId}/interactions` | List interactions |
| POST | `/tasks` | Create a task |
| PATCH | `/tasks/{taskId}` | Update a task (full state) |
| GET | `/leads/{leadId}/tasks` | List tasks, open ones first |

- Moving a card is `PATCH /leads/{leadId}/status`. There is no `/pipelines`,
  no `POST /deals` and no `PATCH /deals/{id}/stage`: the deal appears on its
  own at the proposal, and the stage is the lead's status (D2).
- **Deal:** `{"service": "...", "estimatedValue": 1500.50, "lossReason":
  "..."}`, with at most two decimal places and no negative value. `outcome`
  (`OPEN`, `WON`, `LOST`) is derived from the lead status. Before the proposal,
  `PUT` answers `409`.
- **Interaction:** `type` (`CONTACT`, `REPLY`, `MEETING`, `PROPOSAL`,
  `DO_NOT_CONTACT_REQUEST`, `NOTE`), `channel`, `direction` (`OUTBOUND`,
  `INBOUND`, `INTERNAL`), `occurredAt` (not in the future), `outcome`, `notes`.
  An outbound approach to a `DO_NOT_CONTACT` lead answers `409`.
- **Task:** `{"leadId", "title", "dueAt", "assignedUserId"}`; the owner must be
  an active member (otherwise `404`). `PATCH` takes the full state with
  `status` `OPEN` or `DONE`.
- **Board** (filters `search`, `segment`, `city`): each column carries `total`
  and `totalValue` — the sum over the whole stage in BRL, not only the cards
  shown. `Won` and `Lost` carry only those closed in the last 30 days, with
  `recentDays: 30`; older ones remain in `/leads`. Each card carries
  `estimatedValue` and `stageEnteredAt`, the instant of the last status change
  by recording order, which is the basis of "days in stage".

## 9. Outreach

| Method | Endpoint | Description |
|---|---|---|
| GET | `/leads/{leadId}/outreach-context` | Sender, AI context, and recipient or block per channel |
| POST | `/leads/{leadId}/messages/generate` | Generate a draft |
| GET | `/leads/{leadId}/messages` | List messages |
| PATCH | `/messages/{messageId}` | Edit a draft |
| POST | `/messages/{messageId}/approve` | Approve |
| POST | `/messages/{messageId}/record-send` | Record a confirmed send |
| POST | `/messages/{messageId}/cancel` | Cancel |
| POST | `/suppression-entries` | Register a do-not-contact request |
| GET | `/suppression-entries` | List blocks (`ADMIN`); filters `identifierType` and `search` |
| GET | `/suppression-entries/summary` | Totals per type (`ADMIN`) |
| POST | `/suppression-entries/check` | Check one contact (any member) |

- **`generate`** takes `{channel}` (`WHATSAPP`, `EMAIL`, `INSTAGRAM`,
  `PHONE_CALL`) and answers `201` with the message in `DRAFT` or
  `NEEDS_REVIEW`. `409` when there is a block or the company has no valid
  contact on that channel; `429` beyond 10 generations per user in 5 minutes;
  `502 AI_PROVIDER_ERROR` when the provider fails or its output is rejected,
  with no draft stored.
- **`PATCH /messages/{id}`** takes `{subject?, body}`; a subject only for
  e-mail; per-channel limits of 1,000, 4,000, 1,000 and 2,000 characters.
- **`approve`** takes `{unverifiedClaimsReviewed}`, which must be `true` when
  the message is `NEEDS_REVIEW`.
- **`record-send`** takes `{result: SENT|FAILED, sentAt, notes?,
  markLeadContacted?}`, and only from `APPROVED`. `SENT` creates a `CONTACT`
  interaction in the timeline; the lead moves to `CONTACTED` only with
  `markLeadContacted: true`. It records a send that already happened — it must
  never become a hidden dispatch integration.
- **Suppression.** `POST /suppression-entries` takes `{identifierType:
  PHONE|EMAIL|INSTAGRAM, identifier, channel?, reason, requestedAt?}` from any
  member, and is idempotent per identifier and channel. `GET` is `ADMIN` only;
  `search` accepts a contact in any format and normalises it the same way the
  registration did — a search that does not normalise finds nothing. `POST
  /suppression-entries/check` takes `{identifierType, identifier}` from any
  member and answers only `{recognized, blocked, allChannels, channels}`, with
  no reason, date or author, and is a POST so the contact never reaches the
  URL. There is no removal.

## 10. Specified but not implemented

- **Discovery** (`/discovery/searches`, RF-021). No business source provider
  has been selected, so the routes do not exist. When one is added, the API
  must reject unconfigured or unauthorised sources, and the result limit is an
  interface limit, not authorisation for unlimited collection.
- **Website Studio** (`/website-templates`, `/website-projects`). Removed by
  decision D13.
- **Channel integrations** (RF-054). Future official integrations get their
  own endpoints and controls.

## 11. Endpoint security

- Authentication on every private route.
- Authorisation by role and by organisation; ownership validated per resource.
- Rate limits on login and on AI generation.
- Payload and upload limits.
- Timeouts on integrations.
- No stack traces.
- No tokens, passwords or unnecessary personal content in logs.
- OpenAPI reflects the contracts that are actually implemented.

## 12. Contracts and evolution

Before implementing a module: define the DTOs, the enums and the errors,
define the authorisation rules, update OpenAPI, write contract and integration
tests, and update this document when something relevant changes.
