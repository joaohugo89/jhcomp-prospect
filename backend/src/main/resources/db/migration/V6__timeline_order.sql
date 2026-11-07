-- V6 — A stable order for the lead's history (RF-013).
--
-- The timeline orders by the event's instant. Two events written at the same
-- instant tied and came out in arbitrary order. It does happen: the JVM clock
-- on Windows advances in steps of up to ~15 ms, and the import writes the
-- origin and the lead's first status in the same operation.
--
-- A single sequence shared by both timeline tables records the order in which
-- the events were written and serves as the tiebreak. Existing rows receive
-- values in arbitrary order, which only affects old ties.

CREATE SEQUENCE timeline_event_seq;

ALTER TABLE lead_status_change
    ADD COLUMN recorded_seq bigint NOT NULL DEFAULT nextval('timeline_event_seq');

ALTER TABLE lead_source
    ADD COLUMN recorded_seq bigint NOT NULL DEFAULT nextval('timeline_event_seq');

COMMENT ON COLUMN lead_status_change.recorded_seq IS
  'Ordem de gravacao, compartilhada com lead_source. Desempate da timeline.';
COMMENT ON COLUMN lead_source.recorded_seq IS
  'Ordem de gravacao, compartilhada com lead_status_change. Desempate da timeline.';
