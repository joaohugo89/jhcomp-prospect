-- V5 — Importacao CSV (RF-015, D14, D23).
--
-- Duas fases: a analise grava a importacao e uma linha por registro do arquivo,
-- com o resultado da validacao e da deduplicacao, sem tocar em company. A
-- confirmacao cria as empresas das linhas aceitas. Assim o operador revisa
-- antes de persistir, e o relatorio fica consultavel depois (aceite do
-- RF-015: erros por linha consultaveis).
--
-- Chaves compostas com organization_id desde a criacao, como na V4.

CREATE TABLE import_batch (
    id                  uuid        PRIMARY KEY,
    organization_id     uuid        NOT NULL REFERENCES organization (id),

    file_name           text        NOT NULL,
    -- Hash do conteudo: identifica reenvio do mesmo arquivo (doc 10 §4).
    file_sha256         varchar(64) NOT NULL,
    source_name         text        NOT NULL,
    dataset_note        text,

    -- Cabecalho do arquivo -> campo da empresa, como JSON. Texto, e nao jsonb:
    -- so e lido e escrito inteiro pela aplicacao, nunca consultado por chave.
    column_mapping      text        NOT NULL,

    status              text        NOT NULL,
    total_rows          integer     NOT NULL,
    valid_rows          integer     NOT NULL,
    rejected_rows       integer     NOT NULL,
    duplicate_rows      integer     NOT NULL,
    imported_rows       integer     NOT NULL DEFAULT 0,
    leads_created       integer     NOT NULL DEFAULT 0,

    created_by_user_id  uuid        NOT NULL,
    created_at          timestamptz NOT NULL,
    completed_at        timestamptz,

    CONSTRAINT import_batch_org_id_unique UNIQUE (organization_id, id),
    CONSTRAINT import_batch_creator_same_org
        FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT import_batch_status_valid CHECK (status IN ('ANALYZED', 'IMPORTING', 'COMPLETED', 'DISCARDED')),
    CONSTRAINT import_batch_sha_format CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT import_batch_counts CHECK (
        total_rows >= 0 AND valid_rows >= 0 AND rejected_rows >= 0 AND duplicate_rows >= 0
        AND valid_rows + rejected_rows + duplicate_rows = total_rows
        AND imported_rows >= 0 AND imported_rows <= total_rows
        AND leads_created >= 0 AND leads_created <= imported_rows),
    -- Limite da D14, tambem no banco.
    CONSTRAINT import_batch_row_limit CHECK (total_rows <= 2000),
    CONSTRAINT import_batch_completed_consistent CHECK (
        (status IN ('COMPLETED', 'DISCARDED')) = (completed_at IS NOT NULL))
);

CREATE INDEX import_batch_by_org ON import_batch (organization_id, created_at DESC);
CREATE INDEX import_batch_by_sha ON import_batch (organization_id, file_sha256);

CREATE TABLE import_row (
    id                       uuid    PRIMARY KEY,
    organization_id          uuid    NOT NULL,
    import_id                uuid    NOT NULL,
    -- Linha no arquivo, contando o cabecalho como linha 1, como o operador ve
    -- na planilha.
    row_number               integer NOT NULL,

    status                   text    NOT NULL,

    -- Valores ja mapeados para os campos da empresa. Nulos apos o descarte da
    -- importacao (minimizacao, doc 07 §2).
    name                     text,
    trade_name               text,
    segment                  text,
    city                     text,
    state                    text,
    website_url              text,
    instagram_url            text,
    phone                    text,
    business_email           text,
    address                  text,
    notes                    text,

    -- Erros de validacao como JSON [{"field": ..., "message": ...}].
    errors                   text,

    duplicate_of_company_id  uuid,
    duplicate_of_row_number  integer,
    duplicate_signals        text,

    company_id               uuid,
    lead_id                  uuid,

    CONSTRAINT import_row_batch_same_org
        FOREIGN KEY (organization_id, import_id) REFERENCES import_batch (organization_id, id),
    CONSTRAINT import_row_duplicate_same_org
        FOREIGN KEY (organization_id, duplicate_of_company_id) REFERENCES company (organization_id, id),
    CONSTRAINT import_row_company_same_org
        FOREIGN KEY (organization_id, company_id) REFERENCES company (organization_id, id),
    CONSTRAINT import_row_lead_same_org
        FOREIGN KEY (organization_id, lead_id) REFERENCES lead (organization_id, id),
    CONSTRAINT import_row_status_valid CHECK (status IN ('VALID', 'REJECTED', 'DUPLICATE', 'IMPORTED', 'SKIPPED')),
    CONSTRAINT import_row_number_unique UNIQUE (import_id, row_number),
    CONSTRAINT import_row_imported_has_company CHECK ((status = 'IMPORTED') = (company_id IS NOT NULL))
);

CREATE INDEX import_row_by_import ON import_row (organization_id, import_id, row_number);

COMMENT ON TABLE import_row IS
  'Resultado por linha de uma importacao CSV (RF-015). Nao guarda a linha bruta '
  'do arquivo, apenas os campos mapeados para a empresa.';
