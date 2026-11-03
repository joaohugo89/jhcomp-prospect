-- V15 — Autor da empresa e destino da mesclagem gravados na propria empresa (D32).
--
-- A V14 deduzia quem cadastrou pela primeira proveniencia, e o desfazer
-- importacao pelas linhas do lote. Mesclar move proveniencia e linhas de
-- importacao de uma empresa para outra, e com isso o "autor" e a "importacao
-- de origem" do destino podiam mudar. Os dois fatos passam a ser colunas que
-- nunca se movem.
--
-- Migration imutavel apos aplicada (doc 04 §6). Corrigir em V16, nunca aqui.
ALTER TABLE company ADD COLUMN created_by_user_id uuid;
ALTER TABLE company ADD COLUMN merged_into_company_id uuid;

-- Empresas existentes: autor da primeira proveniencia gravada.
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
