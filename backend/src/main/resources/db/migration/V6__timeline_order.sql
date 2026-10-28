-- V6 — Ordem estavel do historico do lead (RF-013).
--
-- A timeline ordena por instante do evento. Dois eventos gravados no mesmo
-- instante empatavam e saiam em ordem arbitraria. Acontece de fato: o relogio
-- da JVM no Windows avanca em degraus de ate ~15 ms, e a importacao grava a
-- origem e o primeiro status do lead na mesma operacao.
--
-- Uma sequencia unica para as duas tabelas da timeline registra a ordem em
-- que os eventos foram gravados e serve de desempate. Linhas ja existentes
-- recebem valores em ordem arbitraria, o que so afeta empates antigos.

CREATE SEQUENCE timeline_event_seq;

ALTER TABLE lead_status_change
    ADD COLUMN recorded_seq bigint NOT NULL DEFAULT nextval('timeline_event_seq');

ALTER TABLE lead_source
    ADD COLUMN recorded_seq bigint NOT NULL DEFAULT nextval('timeline_event_seq');

COMMENT ON COLUMN lead_status_change.recorded_seq IS
  'Ordem de gravacao, compartilhada com lead_source. Desempate da timeline.';
COMMENT ON COLUMN lead_source.recorded_seq IS
  'Ordem de gravacao, compartilhada com lead_status_change. Desempate da timeline.';
