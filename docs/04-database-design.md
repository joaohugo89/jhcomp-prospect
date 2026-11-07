# 04 — Data model

This document describes the schema **as built**, column by column, against the
Flyway migrations in `backend/src/main/resources/db/migration`. Where the
original design and the implementation diverged, the implementation wins and
the divergence is stated.

## 1. Conventions

- UUID primary keys, assigned by the application rather than the database —
  which is also what lets Hibernate batch inserts.
- `created_at` and `updated_at` on mutable entities.
- Timestamps persisted in UTC (RNF-009).
- `organization_id` mandatory on anything private to an organisation.
- Indexes for the filters that are actually used.
- Soft delete only where the business needs it and a retention policy exists;
  never as a substitute for a legally required deletion.
- Money in `NUMERIC`, never `FLOAT`.
- Enums persisted as strings, with controlled migrations.
- `varchar`, never `char(n)`: `char(n)` becomes `bpchar` and fails Hibernate's
  schema validation, which expects `varchar(n)` for a `String` field.

## 2. Entities

### `organization`

`id`, `name`, `slug`, `status`, `created_at`, `updated_at`

### `app_user`

`id`, `email`, `password_hash`, `display_name`, `status`, `created_at`,
`updated_at`, `password_change_required`, `credentials_changed_at`

`password_change_required` drives the forced change after an admin issues a
temporary password; `credentials_changed_at` is read during membership
revalidation to end the user's other sessions when the password changes (D28).

### `organization_member`

`id`, `organization_id`, `user_id`, `role`, `status`, `created_at`

Unique on `(organization_id, user_id)`.

### `company`

`id`, `organization_id`, `name`, `normalized_name`, `trade_name`, `segment`,
`city`, `state`, `website_url`, `normalized_domain`, `instagram_url`, `phone`,
`normalized_phone`, `business_email`, `address`, `notes`, `archived_at`,
`created_at`, `updated_at`, `created_by_user_id`, `merged_into_company_id`

The normalised columns are filled by the single normalisation component (D10),
the same one the do-not-contact list uses — two implementations would mean a
phone blocked in one place and reachable in the other. `normalized_name` is
persisted so deduplication can use an index (D4); it never replaces the
original name and is never displayed in its place.

`created_by_user_id` supports the deletion rule (D32): whoever registered a
company may delete it within 7 days if it has no commercial activity.
`merged_into_company_id` points at the surviving company after a merge.

There is no per-field verification state on `company`: verification lives in
the analysis and in `lead_source` (D12).

Indexes: `(organization_id, normalized_name, city, state)`,
`(organization_id, segment)`, `(organization_id, city, state)`.

### `lead`

`id`, `organization_id`, `company_id`, `status`, `priority`, `score`,
`score_version`, `assigned_user_id`, `created_at`, `updated_at`, `closed_at`

`status` is the single source of commercial state (D2). `score` holds the score
of the most recently completed analysis (D3).

### `lead_status_change`

`id`, `organization_id`, `lead_id`, `from_status`, `to_status`, `reason`,
`changed_by_user_id`, `changed_at`, `recorded_seq`

`recorded_seq` exists because the JVM clock on Windows has a resolution of up
to ~16 ms: two consecutive changes can receive the same instant, and ordering
by timestamp alone becomes arbitrary. The sequence breaks the tie
deterministically.

### `lead_source`

`id`, `organization_id`, `company_id`, `lead_id`, `source_type`, `source_name`,
`source_reference`, `source_url`, `collected_at`, `collected_by_user_id`,
`terms_or_dataset_note`, `created_at`, `recorded_seq`

`company_id` is mandatory and `lead_id` optional (D6): provenance belongs to
the company, which outlives any individual opportunity. The first record is
what the dashboard reports as the company's origin (D29).

Record where data came from without copying protected content or more data
than necessary.

### `lead_view`

`id`, `organization_id`, `user_id`, `name`, `query`, `created_at`, `shared`

Saved views for the lead work queue (D31). A shared view is published or
deleted only by an admin.

### `lead_analysis`

`id`, `organization_id`, `lead_id`, `analysis_type`, `status`, `score`,
`score_version`, `failure_reason`, `requested_by_user_id`, `created_at`,
`started_at`, `completed_at`

### `analysis_finding`

`id`, `organization_id`, `analysis_id`, `criterion`, `finding_status`,
`original_status`, `description`, `confidence`, `points_awarded`, `max_points`,
`review_status`, `reviewed_by_user_id`, `reviewed_at`, `review_note`

Finding states: `CONFIRMED`, `NOT_FOUND`, `NOT_VERIFIED`, `NOT_APPLICABLE`,
`CONTRADICTED`.

`original_status` keeps what the automated analysis concluded before a human
review changed it, so a reviewed finding still shows what the machine thought.
`max_points` is stored alongside `points_awarded` so a past score stays
explainable after the formula changes.

### `analysis_evidence`

`id`, `organization_id`, `finding_id`, `evidence_type`, `reference_url`,
`description`, `observed_at`, `captured_by`, `content_hash`

Do not store whole copies of pages or unnecessary personal data. A URL and a
concise description are usually enough; `content_hash` proves what was seen
without keeping it.

### `deal`

`id`, `organization_id`, `lead_id`, `service`, `estimated_value`, `currency`,
`loss_reason`, `created_at`, `updated_at`

Narrower than the original design, by D2: no `pipeline_id`, no `stage_id`, no
`outcome`, no `title`. The state is `Lead.status`; a deal holds only the
commercial facts.

### `interaction`

`id`, `organization_id`, `lead_id`, `interaction_type`, `channel`, `direction`,
`occurred_at`, `outcome`, `notes`, `created_by_user_id`, `created_at`,
`recorded_seq`

### `task`

`id`, `organization_id`, `lead_id`, `title`, `due_at`, `status`,
`assigned_user_id`, `created_by_user_id`, `created_at`, `updated_at`,
`completed_at`

### `outreach_message`

`id`, `organization_id`, `lead_id`, `channel`, `subject`, `body`,
`unverified_claims`, `status`, `generated_by`, `prompt_version`,
`created_by_user_id`, `approved_by_user_id`, `approved_at`, `sent_at`,
`send_recorded_by_user_id`, `created_at`, `updated_at`

Statuses: `DRAFT`, `NEEDS_REVIEW`, `APPROVED`, `SENT`, `FAILED`, `CANCELLED`.

`unverified_claims` records the statements the generated text makes that no
confirmed finding supports — the reviewer sees exactly what to check instead
of re-reading the whole message.

`SENT` is applied only when a send was performed and confirmed. Opening a
messaging application is not a send, and `send_recorded_by_user_id` records who
confirmed it.

### `suppression_entry`

`id`, `organization_id`, `identifier_type`, `identifier`, `channel`, `reason`,
`requested_at`, `created_by_user_id`, `created_at`

`identifier` is stored normalised, by the same component as `company`, so a
request made with one phone format blocks every format of that number. Store
the minimum needed to prevent further contact. There is no removal in the MVP
(D26).

### `import_batch`

`id`, `organization_id`, `file_name`, `file_sha256`, `source_name`,
`dataset_note`, `column_mapping`, `ignored_columns`, `status`, `total_rows`,
`valid_rows`, `rejected_rows`, `duplicate_rows`, `imported_rows`,
`merged_rows`, `leads_created`, `created_by_user_id`, `created_at`,
`completed_at`, `undone_at`

Status: `ANALYZED`, `IMPORTING`, `COMPLETED`, `DISCARDED`. `file_sha256`
identifies a resend of the same file; `undone_at` marks an import reverted
under D32.

### `import_row`

`id`, `organization_id`, `import_id`, `row_number`, `status`, the mapped
company fields, `errors`, `duplicate_of_company_id`, `duplicate_of_row_number`,
`duplicate_signals`, `merged_fields`, `company_id`, `lead_id`

Status: `VALID`, `REJECTED`, `DUPLICATE`, `IMPORTED`, `MERGED`, `SKIPPED`,
`REMOVED`.

It does not keep the raw file line, nor any column that did not map to a field.

### `job`

`id`, `organization_id`, `job_type`, `payload`, `status`, `attempts`,
`max_attempts`, `run_after`, `locked_at`, `last_error`, `idempotency_key`,
`created_at`, `updated_at`, `completed_at`

The persistent job table from D15. A table rather than `@Async` because the
work has to survive a restart.

### `audit_event`

`id`, `organization_id`, `actor_user_id`, `action`, `outcome`,
`resource_type`, `resource_id`, `ip_address`, `detail`, `occurred_at`

`ip_address` is `inet`. The JDBC driver will not bind a parameter to `inet`
directly — it arrives as `varchar` with no implicit conversion — so the cast is
explicit in the query (D18).

### Entities that are specified but do not exist

- **`company_contact`** — deferred by D5. The primary contact lives on
  `company`. Reintroduce it when several contacts per company are genuinely
  needed, taking into account that a contact may identify a natural person.
- **`pipeline` and `pipeline_stage`** — there is no table. The board columns
  are the constant `PipelineStages.DEFAULT` in the `crm` module, mapping one
  to one onto `Lead.status` (D2). A table would have allowed the columns and
  the lead status to drift apart, which is the bug the decision prevents.
- **`website_template`, `website_project`, `website_deployment`** — removed
  with the Website Studio (D13). Not to be created.

## 3. Relationships

- `organization` 1:N `organization_member`, `app_user` 1:N
  `organization_member`.
- `organization` 1:N `company`; `company` 1:N `lead`.
- `company` 1:N `lead_source`, optionally tied to a lead.
- `import_batch` 1:N `import_row`.
- `lead` 1:N `lead_analysis` 1:N `analysis_finding` 1:N `analysis_evidence`.
- `lead` 1:N `interaction`, `outreach_message`, `task`, `lead_status_change`.
- `lead` 1:1 `deal`, created when the opportunity acquires commercial facts.

## 4. Integrity and isolation

- Explicit foreign keys.
- Unique membership per organisation and user.
- Related entities are validated to belong to the same organisation.
- A UUID sent by the frontend is never proof of authorisation.
- Every query carries the organisation scope.
- Cross-organisation access is tested negatively.
- **Composite foreign keys including `organization_id`** (migration V4) make a
  cross-tenant association impossible at the database level, not merely
  unlikely at the application level. Every new table follows the same pattern.

## 5. Deduplication

Deduplication combines a public business identifier where legitimately
available, the normalised name, city and state, the website domain, and the
normalised business phone.

Nothing is merged automatically on name similarity alone. The system presents
candidates and a human confirms (D32 governs what happens to the leads and
sources of the merged company).

## 6. Migrations

Flyway, with `spring-boot-starter-flyway` — declaring only `flyway-core`, as
one would on Boot 3, puts the library on the classpath without enabling the
autoconfiguration, and **no migration is applied, silently**. Always confirm
the `Successfully applied N migrations` line.

- Applied migrations are immutable. Never edit one; add the next version.
- Indexes follow real queries.
- Minimal seed data for roles.
- No manual change in production outside the migration process.
- Migrations are tested against a real PostgreSQL through Testcontainers.
- `flyway_schema_history.version` is text, so `max(version)` returns `9` when
  V10 is applied. Order by `installed_rank`.
