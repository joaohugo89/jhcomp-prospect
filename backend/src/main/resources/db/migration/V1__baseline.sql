-- V1 — Schema baseline.
--
-- This migration creates no domain entity: the identity and organisation
-- tables arrive in stage 3 (V2). Only the database prerequisites used across
-- the whole project live here.
--
-- Applied migrations are immutable (doc 04 §6). To correct anything defined
-- here, create the next version; never edit this file.

-- unaccent is used by the name normalisation shared between deduplication
-- (doc 06 §4) and the do-not-contact list (doc 07 §10) — decision D10.
-- Installed in the baseline because creating an extension requires elevated
-- privilege, which the application user may not have in managed
-- environments.
CREATE EXTENSION IF NOT EXISTS unaccent;

COMMENT ON EXTENSION unaccent IS
  'Normalizacao de nomes para deduplicacao e lista de nao contato (D10).';
