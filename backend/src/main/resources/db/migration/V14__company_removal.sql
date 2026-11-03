-- V14 — Excluir empresa cadastrada por engano e desfazer importacao (D32).
--
-- Excluir apaga a empresa de verdade, mas so quando nao ha atividade comercial
-- sobre ela. A linha de importacao que a criou (ou completou) continua no lote,
-- com status REMOVED e sem empresa: o lote nao perde o historico do que trouxe.
-- undone_at marca o lote que foi desfeito.
--
-- Migration imutavel apos aplicada (doc 04 §6). Corrigir em V15, nunca aqui.
ALTER TABLE import_row DROP CONSTRAINT import_row_status_valid;
ALTER TABLE import_row ADD CONSTRAINT import_row_status_valid
    CHECK (status IN ('VALID', 'REJECTED', 'DUPLICATE', 'IMPORTED', 'SKIPPED', 'MERGED', 'REMOVED'));

ALTER TABLE import_batch ADD COLUMN undone_at timestamptz;

COMMENT ON COLUMN import_batch.undone_at IS
  'Quando a importacao foi desfeita: empresas criadas e sem atividade foram excluidas (D32).';
