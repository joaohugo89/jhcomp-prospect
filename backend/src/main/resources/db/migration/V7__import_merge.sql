-- V7 — Completar empresa existente a partir da importacao (D23, revisada).
--
-- Uma linha marcada como possivel duplicata de empresa ja cadastrada pode,
-- por escolha do operador, preencher os campos VAZIOS dessa empresa. Nunca
-- sobrescreve valor existente (RF-023: nao sobrescrever silenciosamente).

ALTER TABLE import_batch ADD COLUMN merged_rows integer NOT NULL DEFAULT 0;

ALTER TABLE import_batch DROP CONSTRAINT import_batch_counts;
ALTER TABLE import_batch ADD CONSTRAINT import_batch_counts CHECK (
    total_rows >= 0 AND valid_rows >= 0 AND rejected_rows >= 0 AND duplicate_rows >= 0
    AND valid_rows + rejected_rows + duplicate_rows = total_rows
    AND imported_rows >= 0 AND merged_rows >= 0
    AND imported_rows + merged_rows <= total_rows
    AND leads_created >= 0 AND leads_created <= imported_rows + merged_rows);

-- Campos que a linha preencheu na empresa existente, como JSON.
ALTER TABLE import_row ADD COLUMN merged_fields text;

ALTER TABLE import_row DROP CONSTRAINT import_row_status_valid;
ALTER TABLE import_row ADD CONSTRAINT import_row_status_valid
    CHECK (status IN ('VALID', 'REJECTED', 'DUPLICATE', 'IMPORTED', 'MERGED', 'SKIPPED'));

ALTER TABLE import_row DROP CONSTRAINT import_row_imported_has_company;
ALTER TABLE import_row ADD CONSTRAINT import_row_imported_has_company
    CHECK ((status IN ('IMPORTED', 'MERGED')) = (company_id IS NOT NULL));

ALTER TABLE import_row ADD CONSTRAINT import_row_merged_fields_only_when_merged
    CHECK (merged_fields IS NULL OR status = 'MERGED');
