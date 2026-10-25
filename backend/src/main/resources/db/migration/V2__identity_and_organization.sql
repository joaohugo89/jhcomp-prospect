-- V2 — Identidade, organizacao e auditoria (RF-001, RF-002, RF-003).
--
-- Convencoes do doc 04 §1: PK uuid, created_at/updated_at em entidades
-- mutaveis, timestamps em UTC (timestamptz), enums como texto com CHECK.
--
-- Migration imutavel apos aplicada (doc 04 §6). Corrigir em V3, nunca aqui.

-- ---------------------------------------------------------------------------
-- Organization
-- ---------------------------------------------------------------------------
CREATE TABLE organization (
    id         uuid        PRIMARY KEY,
    name       text        NOT NULL,
    slug       text        NOT NULL,
    status     text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT organization_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT organization_slug_format     CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT organization_status_valid    CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX organization_slug_key ON organization (slug);

-- ---------------------------------------------------------------------------
-- app_user
--
-- Nao se chama "user": e palavra reservada no PostgreSQL e exigiria aspas em
-- toda consulta.
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id            uuid        PRIMARY KEY,
    email         text        NOT NULL,
    password_hash text        NOT NULL,
    display_name  text        NOT NULL,
    status        text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT app_user_email_format     CHECK (email = lower(btrim(email)) AND position('@' in email) > 1),
    CONSTRAINT app_user_display_not_blank CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT app_user_status_valid      CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- Unicidade de e-mail. A coluna ja e normalizada para minusculas pelo CHECK,
-- portanto o indice simples basta e serve a busca de login.
CREATE UNIQUE INDEX app_user_email_key ON app_user (email);

COMMENT ON COLUMN app_user.password_hash IS
  'Hash com prefixo do algoritmo (ex. {bcrypt}). Nunca senha em texto puro (doc 07 §4).';

-- ---------------------------------------------------------------------------
-- organization_member
-- ---------------------------------------------------------------------------
CREATE TABLE organization_member (
    id              uuid        PRIMARY KEY,
    organization_id uuid        NOT NULL REFERENCES organization (id),
    user_id         uuid        NOT NULL REFERENCES app_user (id),
    role            text        NOT NULL,
    status          text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT organization_member_role_valid   CHECK (role IN ('ADMIN', 'OPERATOR')),
    CONSTRAINT organization_member_status_valid CHECK (status IN ('ACTIVE', 'REVOKED'))
);

-- Restricao unica exigida pelo doc 04 §2.
CREATE UNIQUE INDEX organization_member_unique
    ON organization_member (organization_id, user_id);

-- Indice iniciando por organization_id, conforme doc 03 §8.
CREATE INDEX organization_member_by_organization
    ON organization_member (organization_id, status);

CREATE INDEX organization_member_by_user
    ON organization_member (user_id, status);

-- ---------------------------------------------------------------------------
-- audit_event (RNF-008, doc 07 §11)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_event (
    id              uuid        PRIMARY KEY,
    organization_id uuid        REFERENCES organization (id),
    actor_user_id   uuid        REFERENCES app_user (id),
    action          text        NOT NULL,
    outcome         text        NOT NULL,
    resource_type   text,
    resource_id     text,
    -- text, e nao inet: o driver envia o parametro como varchar e o
    -- PostgreSQL nao faz a conversao implicita para inet, o que quebra a
    -- insercao. A validacao de formato nao compensa o atrito de mapeamento.
    ip_address      text,
    detail          text,
    occurred_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT audit_event_outcome_valid CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT audit_event_detail_length CHECK (detail IS NULL OR length(detail) <= 500),
    CONSTRAINT audit_event_ip_length     CHECK (ip_address IS NULL OR length(ip_address) <= 45)
);

CREATE INDEX audit_event_by_organization ON audit_event (organization_id, occurred_at DESC);
CREATE INDEX audit_event_by_actor        ON audit_event (actor_user_id, occurred_at DESC);
CREATE INDEX audit_event_by_action       ON audit_event (action, occurred_at DESC);

-- organization_id e actor_user_id sao nulos de proposito: uma falha de login
-- ocorre antes de haver usuario ou organizacao identificados.
COMMENT ON TABLE audit_event IS
  'Trilha de auditoria de acoes criticas. Nao armazenar conteudo de mensagem, '
  'senha, token nem dado pessoal desnecessario (doc 07 §6).';

COMMENT ON COLUMN audit_event.detail IS
  'Resumo curto e nao sensivel. Limitado a 500 caracteres.';
