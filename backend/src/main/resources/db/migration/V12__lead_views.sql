-- V12 — Saved views of the lead list (D31).
--
-- Each member keeps their own searches ("my proposals", "imported
-- yesterday"). A view belongs to whoever created it: not even the
-- administrator sees anyone else's. The stored query is the list's query
-- string; what applies it is the screen, which reads only the filters it
-- knows. Working on any device is why it lives in the database.
--
-- Immutable once applied (doc 04 §6). Correct in V13, never here.
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

-- One name per person, case-insensitively: two "Propostas" would be confusing.
CREATE UNIQUE INDEX lead_view_name_per_user ON lead_view (organization_id, user_id, lower(name));
