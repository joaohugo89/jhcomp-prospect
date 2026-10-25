-- V1 — Baseline do schema.
--
-- Esta migration nao cria entidade de dominio: as tabelas de identidade e
-- organizacao entram na Etapa 3 (V2). Aqui ficam apenas os pre-requisitos de
-- banco usados por todo o projeto.
--
-- Migrations aplicadas sao imutaveis (doc 04 §6). Para corrigir algo definido
-- aqui, criar a proxima versao; nunca editar este arquivo.

-- unaccent e usado pela normalizacao de nomes compartilhada entre a
-- deduplicacao (doc 06 §4) e a lista de nao contato (doc 07 §10) — decisao D10.
-- Instalado no baseline porque criar extensao exige privilegio elevado, que o
-- usuario da aplicacao pode nao ter nos ambientes gerenciados.
CREATE EXTENSION IF NOT EXISTS unaccent;

COMMENT ON EXTENSION unaccent IS
  'Normalizacao de nomes para deduplicacao e lista de nao contato (D10).';
