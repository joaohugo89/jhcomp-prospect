-- V5 — CSV import (RF-015, D14, D23).
--
-- Two phases: the analysis writes the import and one row per record in the
-- file, with the validation and deduplication results, without touching
-- company. The confirmation creates the companies of the accepted rows. That
-- way the operator reviews before anything is persisted, and the report stays
-- readable afterwards (RF-015's acceptance criterion: per-row errors are
-- inspectable).
--
-- Composite keys with organization_id from creation, as in V4.

CREATE TABLE import_batch (
    id                  uuid        PRIMARY KEY,
    organization_id     uuid        NOT NULL REFERENCES organization (id),

    file_name           text        NOT NULL,
    -- The content hash: it identifies a resend of the same file (doc 10 §4).
    file_sha256         varchar(64) NOT NULL,
    source_name         text        NOT NULL,
    dataset_note        text,

    -- File header -> company field, as JSON. Text, not jsonb: the application
    -- only reads and writes it whole, never queries it by key.
    column_mapping      text        NOT NULL,
    -- The file's headers that became no field, as JSON. Kept so the operator
    -- can check the mapping; the values of those columns are never read.
    ignored_columns     text        NOT NULL,

    status              text        NOT NULL,
    total_rows          integer     NOT NULL,
    valid_rows          integer     NOT NULL,
    rejected_rows       integer     NOT NULL,
    duplicate_rows      integer     NOT NULL,
    imported_rows       integer     NOT NULL DEFAULT 0,
    leads_created       integer     NOT NULL DEFAULT 0,

    created_by_user_id  uuid        NOT NULL,
    created_at          timestamptz NOT NULL,
    completed_at        timestamptz,

    CONSTRAINT import_batch_org_id_unique UNIQUE (organization_id, id),
    CONSTRAINT import_batch_creator_same_org
        FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT import_batch_status_valid CHECK (status IN ('ANALYZED', 'IMPORTING', 'COMPLETED', 'DISCARDED')),
    CONSTRAINT import_batch_sha_format CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT import_batch_counts CHECK (
        total_rows >= 0 AND valid_rows >= 0 AND rejected_rows >= 0 AND duplicate_rows >= 0
        AND valid_rows + rejected_rows + duplicate_rows = total_rows
        AND imported_rows >= 0 AND imported_rows <= total_rows
        AND leads_created >= 0 AND leads_created <= imported_rows),
    -- D14's limit, enforced in the database too.
    CONSTRAINT import_batch_row_limit CHECK (total_rows <= 2000),
    CONSTRAINT import_batch_completed_consistent CHECK (
        (status IN ('COMPLETED', 'DISCARDED')) = (completed_at IS NOT NULL))
);

CREATE INDEX import_batch_by_org ON import_batch (organization_id, created_at DESC);
CREATE INDEX import_batch_by_sha ON import_batch (organization_id, file_sha256);

CREATE TABLE import_row (
    id                       uuid    PRIMARY KEY,
    organization_id          uuid    NOT NULL,
    import_id                uuid    NOT NULL,
    -- The line in the file, counting the header as line 1, as the operator
    -- sees it in the spreadsheet.
    row_number               integer NOT NULL,

    status                   text    NOT NULL,

    -- Values already mapped to the company's fields. Null once the import is
    -- discarded (minimisation, doc 07 §2).
    name                     text,
    trade_name               text,
    segment                  text,
    city                     text,
    state                    text,
    website_url              text,
    instagram_url            text,
    phone                    text,
    business_email           text,
    address                  text,
    notes                    text,

    -- Validation errors as JSON [{"field": ..., "message": ...}].
    errors                   text,

    duplicate_of_company_id  uuid,
    duplicate_of_row_number  integer,
    duplicate_signals        text,

    company_id               uuid,
    lead_id                  uuid,

    CONSTRAINT import_row_batch_same_org
        FOREIGN KEY (organization_id, import_id) REFERENCES import_batch (organization_id, id),
    CONSTRAINT import_row_duplicate_same_org
        FOREIGN KEY (organization_id, duplicate_of_company_id) REFERENCES company (organization_id, id),
    CONSTRAINT import_row_company_same_org
        FOREIGN KEY (organization_id, company_id) REFERENCES company (organization_id, id),
    CONSTRAINT import_row_lead_same_org
        FOREIGN KEY (organization_id, lead_id) REFERENCES lead (organization_id, id),
    CONSTRAINT import_row_status_valid CHECK (status IN ('VALID', 'REJECTED', 'DUPLICATE', 'IMPORTED', 'SKIPPED')),
    CONSTRAINT import_row_number_unique UNIQUE (import_id, row_number),
    CONSTRAINT import_row_imported_has_company CHECK ((status = 'IMPORTED') = (company_id IS NOT NULL))
);

CREATE INDEX import_row_by_import ON import_row (organization_id, import_id, row_number);

COMMENT ON TABLE import_row IS
  'Resultado por linha de uma importacao CSV (RF-015). Nao guarda a linha bruta '
  'do arquivo, apenas os campos mapeados para a empresa.';
