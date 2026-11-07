-- V7 — Completing an existing company from an import (D23, revised).
--
-- A row flagged as a possible duplicate of an already registered company may,
-- at the operator's choice, fill that company's EMPTY fields. It never
-- overwrites an existing value (RF-023: no silent overwriting).

ALTER TABLE import_batch ADD COLUMN merged_rows integer NOT NULL DEFAULT 0;

ALTER TABLE import_batch DROP CONSTRAINT import_batch_counts;
ALTER TABLE import_batch ADD CONSTRAINT import_batch_counts CHECK (
    total_rows >= 0 AND valid_rows >= 0 AND rejected_rows >= 0 AND duplicate_rows >= 0
    AND valid_rows + rejected_rows + duplicate_rows = total_rows
    AND imported_rows >= 0 AND merged_rows >= 0
    AND imported_rows + merged_rows <= total_rows
    AND leads_created >= 0 AND leads_created <= imported_rows + merged_rows);

-- The fields this row filled in on the existing company, as JSON.
ALTER TABLE import_row ADD COLUMN merged_fields text;

ALTER TABLE import_row DROP CONSTRAINT import_row_status_valid;
ALTER TABLE import_row ADD CONSTRAINT import_row_status_valid
    CHECK (status IN ('VALID', 'REJECTED', 'DUPLICATE', 'IMPORTED', 'MERGED', 'SKIPPED'));

ALTER TABLE import_row DROP CONSTRAINT import_row_imported_has_company;
ALTER TABLE import_row ADD CONSTRAINT import_row_imported_has_company
    CHECK ((status IN ('IMPORTED', 'MERGED')) = (company_id IS NOT NULL));

ALTER TABLE import_row ADD CONSTRAINT import_row_merged_fields_only_when_merged
    CHECK (merged_fields IS NULL OR status = 'MERGED');
