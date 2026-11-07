-- V14 — Deleting a company registered by mistake, and undoing an import
-- (D32).
--
-- Deleting really removes the company, but only when it carries no commercial
-- activity. The import row that created (or completed) it stays in the batch,
-- with status REMOVED and no company: the batch does not lose the history of
-- what it brought. undone_at marks a batch that was undone.
--
-- Immutable once applied (doc 04 §6). Correct in V15, never here.
ALTER TABLE import_row DROP CONSTRAINT import_row_status_valid;
ALTER TABLE import_row ADD CONSTRAINT import_row_status_valid
    CHECK (status IN ('VALID', 'REJECTED', 'DUPLICATE', 'IMPORTED', 'SKIPPED', 'MERGED', 'REMOVED'));

ALTER TABLE import_batch ADD COLUMN undone_at timestamptz;

COMMENT ON COLUMN import_batch.undone_at IS
  'Quando a importacao foi desfeita: empresas criadas e sem atividade foram excluidas (D32).';
