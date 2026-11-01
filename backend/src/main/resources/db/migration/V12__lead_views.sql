-- V12 — Visoes salvas da lista de leads (D31).
--
-- Cada membro guarda as suas buscas ("Minhas propostas", "Importados ontem").
-- A visao e de quem a criou: nem o administrador ve as dos outros. A consulta
-- guardada e a query string da lista; quem a aplica e a tela, que so le os
-- filtros que conhece. Valer em qualquer aparelho e o motivo de estar no banco.
--
-- Migration imutavel apos aplicada (doc 04 §6). Corrigir em V13, nunca aqui.
CREATE TABLE lead_view (
    id               uuid         PRIMARY KEY,
    organization_id  uuid         NOT NULL,
    user_id          uuid         NOT NULL,
    name             varchar(60)  NOT NULL,
    query            varchar(500) NOT NULL,
    created_at       timestamptz  NOT NULL,

    CONSTRAINT lead_view_owner_same_org
        FOREIGN KEY (organization_id, user_id) REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT lead_view_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- Nome unico por pessoa, sem distinguir maiusculas: duas "Propostas" seria confuso.
CREATE UNIQUE INDEX lead_view_name_per_user ON lead_view (organization_id, user_id, lower(name));
