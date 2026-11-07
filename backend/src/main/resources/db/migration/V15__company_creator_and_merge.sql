-- V15 — The company's author and merge target stored on the company itself
-- (D32).
--
-- V14 inferred who registered a company from its first provenance, and the
-- import undo from the batch's rows. Merging moves provenance and import rows
-- from one company to another, and with that the target's "author" and
-- "originating import" could change. Both facts are now columns that never
-- move.
--
-- Immutable once applied (doc 04 §6). Correct in V16, never here.
ALTER TABLE company ADD COLUMN created_by_user_id uuid;
ALTER TABLE company ADD COLUMN merged_into_company_id uuid;

-- Existing companies: the author of the first recorded provenance.
UPDATE company c
   SET created_by_user_id = (
       SELECT s.collected_by_user_id FROM lead_source s
        WHERE s.organization_id = c.organization_id AND s.company_id = c.id
        ORDER BY s.recorded_seq LIMIT 1);

ALTER TABLE company ADD CONSTRAINT company_creator_same_org
    FOREIGN KEY (organization_id, created_by_user_id) REFERENCES organization_member (organization_id, user_id);
ALTER TABLE company ADD CONSTRAINT company_merged_into_same_org
    FOREIGN KEY (organization_id, merged_into_company_id) REFERENCES company (organization_id, id);
ALTER TABLE company ADD CONSTRAINT company_not_merged_into_itself
    CHECK (merged_into_company_id IS NULL OR merged_into_company_id <> id);

CREATE INDEX company_by_merged_into ON company (organization_id, merged_into_company_id)
    WHERE merged_into_company_id IS NOT NULL;

COMMENT ON COLUMN company.created_by_user_id IS
  'Quem cadastrou ou importou; nao muda com mesclagem. Nulo so em dado anterior sem proveniencia.';
COMMENT ON COLUMN company.merged_into_company_id IS
  'Empresa que absorveu esta na mesclagem (D32). Destino de mesclagem nunca e excluido.';
