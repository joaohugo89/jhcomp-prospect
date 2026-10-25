-- V3 — Empresas, leads e proveniencia (RF-010, RF-011, RF-012, RF-013,
-- RF-020, RF-022).
--
-- Decisoes aplicadas:
--   D4  normalized_name e coluna persistida, para que a deduplicacao use indice.
--   D5  CompanyContact nao existe no MVP; o contato principal fica em company.
--   D6  lead_source.company_id e obrigatorio; lead_id e opcional.
--   D10 as colunas normalizadas sao preenchidas pelo ContactNormalizer.
--
-- Migration imutavel apos aplicada (doc 04 §6). Corrigir em V4, nunca aqui.

-- ---------------------------------------------------------------------------
-- company
-- ---------------------------------------------------------------------------
CREATE TABLE company (
    id               uuid        PRIMARY KEY,
    organization_id  uuid        NOT NULL REFERENCES organization (id),

    name             text        NOT NULL,
    -- Valor de comparacao: sem acento, sem espaco excedente, em minusculas.
    -- O nome original e sempre preservado em name (doc 04 §2).
    normalized_name  text        NOT NULL,
    trade_name       text,
    segment          text,
    city             text        NOT NULL,
    -- varchar(2), e nao char(2): char mapeia para bpchar, e a validacao de
    -- schema do Hibernate espera varchar para um campo String com length 2.
    state            varchar(2)  NOT NULL,

    website_url      text,
    -- Dominio registravel, sem esquema e sem www. Sinal de deduplicacao mais
    -- confiavel depois de um identificador empresarial (doc 06 §4).
    normalized_domain text,
    instagram_url    text,

    phone            text,
    -- Telefone em E.164. Nulo quando o valor informado nao permite
    -- normalizacao confiavel, por exemplo sem DDD.
    normalized_phone text,
    business_email   text,

    address          text,
    notes            text,

    -- Arquivamento e requisito do RF-010. Nao e exclusao: a politica de
    -- retencao e de exclusao sera definida na etapa de privacidade.
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

-- Indices iniciando por organization_id, conforme doc 03 §8.
CREATE INDEX company_by_normalized_name ON company (organization_id, normalized_name, city, state);
CREATE INDEX company_by_segment         ON company (organization_id, segment);
CREATE INDEX company_by_city            ON company (organization_id, city, state);

-- Parciais: a maioria das empresas nao tem site nem telefone cadastrado, e um
-- indice sobre muitas linhas nulas so ocuparia espaco.
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

    -- Fonte unica do estado comercial (decisao D2). Deal, quando existir,
    -- guardara apenas valor, servico e motivo de perda.
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
    -- Score e 0 a 100 e explicavel (doc 06 §7).
    CONSTRAINT lead_score_range CHECK (score IS NULL OR (score >= 0 AND score <= 100)),
    -- Score sem versao da formula nao e reproduzivel.
    CONSTRAINT lead_score_versioned CHECK (score IS NULL OR score_version IS NOT NULL),
    -- Estado terminal exige data de fechamento, e vice-versa.
    CONSTRAINT lead_closed_consistent CHECK (
        (status IN ('WON', 'LOST') AND closed_at IS NOT NULL)
        OR (status NOT IN ('WON', 'LOST') AND closed_at IS NULL))
);

CREATE INDEX lead_by_status   ON lead (organization_id, status, created_at DESC);
CREATE INDEX lead_by_priority ON lead (organization_id, priority, created_at DESC);
CREATE INDEX lead_by_company  ON lead (organization_id, company_id);
CREATE INDEX lead_by_score    ON lead (organization_id, score DESC) WHERE score IS NOT NULL;

-- Impede mais de uma oportunidade aberta por empresa. No MVP vale uma
-- oportunidade ativa por lead (doc 06 §3); um novo ciclo comercial exige
-- encerrar o anterior.
CREATE UNIQUE INDEX lead_one_open_per_company
    ON lead (organization_id, company_id)
    WHERE closed_at IS NULL;

-- ---------------------------------------------------------------------------
-- lead_source — proveniencia (RF-020, RF-022)
-- ---------------------------------------------------------------------------
CREATE TABLE lead_source (
    id                    uuid        PRIMARY KEY,
    organization_id       uuid        NOT NULL REFERENCES organization (id),
    -- Obrigatorio (D6): a proveniencia descreve a origem do dado da empresa.
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
-- lead_status_change — historico do ciclo do lead (RF-013)
-- ---------------------------------------------------------------------------
CREATE TABLE lead_status_change (
    id               uuid        PRIMARY KEY,
    organization_id  uuid        NOT NULL REFERENCES organization (id),
    lead_id          uuid        NOT NULL REFERENCES lead (id),

    -- Nulo na criacao do lead, quando nao havia estado anterior.
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
