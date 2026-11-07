-- V3 — Companies, leads and provenance (RF-010, RF-011, RF-012, RF-013,
-- RF-020, RF-022).
--
-- Decisions applied:
--   D4  normalized_name is a persisted column, so deduplication can use an index.
--   D5  CompanyContact does not exist in the MVP; the primary contact lives on company.
--   D6  lead_source.company_id is mandatory; lead_id is optional.
--   D10 the normalised columns are filled by ContactNormalizer.
--
-- Immutable once applied (doc 04 §6). Correct in V4, never here.

-- ---------------------------------------------------------------------------
-- company
-- ---------------------------------------------------------------------------
CREATE TABLE company (
    id               uuid        PRIMARY KEY,
    organization_id  uuid        NOT NULL REFERENCES organization (id),

    name             text        NOT NULL,
    -- The comparison value: no accents, no excess whitespace, lower case.
    -- The original name is always preserved in name (doc 04 §2).
    normalized_name  text        NOT NULL,
    trade_name       text,
    segment          text,
    city             text        NOT NULL,
    -- varchar(2), not char(2): char maps to bpchar, and Hibernate's schema
    -- validation expects varchar for a String field of length 2.
    state            varchar(2)  NOT NULL,

    website_url      text,
    -- The registrable domain, with no scheme and no www. The most reliable
    -- deduplication signal after a business identifier (doc 06 §4).
    normalized_domain text,
    instagram_url    text,

    phone            text,
    -- The phone in E.164. Null when the value given does not allow confident
    -- normalisation, for example with no area code.
    normalized_phone text,
    business_email   text,

    address          text,
    notes            text,

    -- Archiving is required by RF-010. It is not deletion: the retention and
    -- deletion policy is settled in the privacy stage.
    archived_at      timestamptz,

    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT company_name_not_blank       CHECK (length(btrim(name)) > 0),
    CONSTRAINT company_normalized_not_blank CHECK (length(btrim(normalized_name)) > 0),
    CONSTRAINT company_city_not_blank       CHECK (length(btrim(city)) > 0),
    CONSTRAINT company_state_format         CHECK (state ~ '^[A-Z]{2}$'),
    CONSTRAINT company_phone_e164           CHECK (normalized_phone IS NULL OR normalized_phone ~ '^\+[0-9]{10,15}$'),
    CONSTRAINT company_domain_lowercase     CHECK (normalized_domain IS NULL OR normalized_domain = lower(normalized_domain))
);

-- Indexes starting with organization_id, per doc 03 §8.
CREATE INDEX company_by_normalized_name ON company (organization_id, normalized_name, city, state);
CREATE INDEX company_by_segment         ON company (organization_id, segment);
CREATE INDEX company_by_city            ON company (organization_id, city, state);

-- Partial: most companies have neither a site nor a phone on file, and an
-- index over many null rows would only take up space.
CREATE INDEX company_by_domain ON company (organization_id, normalized_domain)
    WHERE normalized_domain IS NOT NULL;
CREATE INDEX company_by_phone  ON company (organization_id, normalized_phone)
    WHERE normalized_phone IS NOT NULL;

COMMENT ON COLUMN company.normalized_name IS
  'Valor de comparacao para deduplicacao (D4). Nunca exibir: use name.';

-- ---------------------------------------------------------------------------
-- lead
-- ---------------------------------------------------------------------------
CREATE TABLE lead (
    id               uuid        PRIMARY KEY,
    organization_id  uuid        NOT NULL REFERENCES organization (id),
    company_id       uuid        NOT NULL REFERENCES company (id),

    -- The single source of commercial state (decision D2). Deal, once it
    -- exists, will hold only value, service and loss reason.
    status           text        NOT NULL,
    priority         text        NOT NULL,

    score            integer,
    score_version    text,
    assigned_user_id uuid        REFERENCES app_user (id),

    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    closed_at        timestamptz,

    CONSTRAINT lead_status_valid CHECK (status IN (
        'NEW', 'QUALIFIED', 'CONTACT_APPROVED', 'CONTACTED', 'IN_CONVERSATION',
        'PROPOSAL_SENT', 'WON', 'LOST', 'DO_NOT_CONTACT')),
    CONSTRAINT lead_priority_valid CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH')),
    -- The score runs 0 to 100 and is explainable (doc 06 §7).
    CONSTRAINT lead_score_range CHECK (score IS NULL OR (score >= 0 AND score <= 100)),
    -- A score without the formula version is not reproducible.
    CONSTRAINT lead_score_versioned CHECK (score IS NULL OR score_version IS NOT NULL),
    -- A terminal state requires a closing date, and vice versa.
    CONSTRAINT lead_closed_consistent CHECK (
        (status IN ('WON', 'LOST') AND closed_at IS NOT NULL)
        OR (status NOT IN ('WON', 'LOST') AND closed_at IS NULL))
);

CREATE INDEX lead_by_status   ON lead (organization_id, status, created_at DESC);
CREATE INDEX lead_by_priority ON lead (organization_id, priority, created_at DESC);
CREATE INDEX lead_by_company  ON lead (organization_id, company_id);
CREATE INDEX lead_by_score    ON lead (organization_id, score DESC) WHERE score IS NOT NULL;

-- Prevents more than one open opportunity per company. In the MVP one active
-- opportunity per lead applies (doc 06 §3); a new commercial cycle requires
-- closing the previous one.
CREATE UNIQUE INDEX lead_one_open_per_company
    ON lead (organization_id, company_id)
    WHERE closed_at IS NULL;

-- ---------------------------------------------------------------------------
-- lead_source — provenance (RF-020, RF-022)
-- ---------------------------------------------------------------------------
CREATE TABLE lead_source (
    id                    uuid        PRIMARY KEY,
    organization_id       uuid        NOT NULL REFERENCES organization (id),
    -- Mandatory (D6): provenance describes the origin of the company's data.
    company_id            uuid        NOT NULL REFERENCES company (id),
    lead_id               uuid        REFERENCES lead (id),

    source_type           text        NOT NULL,
    source_name           text        NOT NULL,
    source_reference      text,
    source_url            text,

    collected_at          timestamptz NOT NULL,
    collected_by_user_id  uuid        REFERENCES app_user (id),
    terms_or_dataset_note text,

    created_at            timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT lead_source_type_valid CHECK (source_type IN ('MANUAL', 'CSV_IMPORT', 'INTEGRATION')),
    CONSTRAINT lead_source_name_not_blank CHECK (length(btrim(source_name)) > 0),
    CONSTRAINT lead_source_note_length CHECK (terms_or_dataset_note IS NULL OR length(terms_or_dataset_note) <= 1000)
);

CREATE INDEX lead_source_by_company ON lead_source (organization_id, company_id, collected_at DESC);

COMMENT ON TABLE lead_source IS
  'Origem e data de coleta de cada dado (RF-022). Registrar a fonte sem copiar '
  'conteudo protegido nem dado alem do necessario (doc 04 §2).';

-- ---------------------------------------------------------------------------
-- lead_status_change — the lead lifecycle history (RF-013)
-- ---------------------------------------------------------------------------
CREATE TABLE lead_status_change (
    id               uuid        PRIMARY KEY,
    organization_id  uuid        NOT NULL REFERENCES organization (id),
    lead_id          uuid        NOT NULL REFERENCES lead (id),

    -- Null when the lead was created, as there was no previous state.
    from_status      text,
    to_status        text        NOT NULL,
    reason           text,

    changed_by_user_id uuid      NOT NULL REFERENCES app_user (id),
    changed_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT lead_status_change_reason_length CHECK (reason IS NULL OR length(reason) <= 500)
);

CREATE INDEX lead_status_change_by_lead ON lead_status_change (organization_id, lead_id, changed_at DESC);

COMMENT ON TABLE lead_status_change IS
  'Mudancas de estado com ator, data e estado anterior (doc 06 §2). Historico '
  'cronologico e nao alteravel por quem nao tem permissao (RF-013).';
