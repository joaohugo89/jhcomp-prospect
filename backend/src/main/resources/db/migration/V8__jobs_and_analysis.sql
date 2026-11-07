-- V8 — Persistent jobs (D15) and digital presence analysis (RF-030 to
-- RF-033).
--
-- Composite keys with organization_id from creation, as in V4.

-- ---------------------------------------------------------------------------
-- job — the persistent queue, consumed by polling (docs/03 §7, D15)
-- ---------------------------------------------------------------------------
CREATE TABLE job (
    id               uuid        PRIMARY KEY,
    organization_id  uuid        NOT NULL REFERENCES organization (id),
    job_type         text        NOT NULL,
    -- JSON validated by the application before being written.
    payload          text        NOT NULL,
    status           text        NOT NULL,
    attempts         integer     NOT NULL DEFAULT 0,
    max_attempts     integer     NOT NULL,
    -- The next attempt no earlier than this instant (backoff between attempts).
    run_after        timestamptz NOT NULL,
    -- The start of the execution in progress. A RUNNING job with an old
    -- locked_at belongs to a worker that died and becomes eligible again.
    locked_at        timestamptz,
    -- A short summary, with no personal data and no stack trace.
    last_error       text,
    idempotency_key  text,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    completed_at     timestamptz,

    CONSTRAINT job_status_valid CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT job_attempts_valid CHECK (attempts >= 0 AND max_attempts >= 1 AND attempts <= max_attempts),
    CONSTRAINT job_last_error_length CHECK (last_error IS NULL OR length(last_error) <= 500),
    CONSTRAINT job_completed_consistent CHECK ((status IN ('SUCCEEDED', 'FAILED')) = (completed_at IS NOT NULL))
);

-- It does not enqueue the same work twice while the first one is still active.
CREATE UNIQUE INDEX job_active_idempotency
    ON job (organization_id, job_type, idempotency_key)
    WHERE status IN ('PENDING', 'RUNNING') AND idempotency_key IS NOT NULL;

CREATE INDEX job_ready ON job (run_after) WHERE status = 'PENDING';
CREATE INDEX job_running ON job (locked_at) WHERE status = 'RUNNING';

-- ---------------------------------------------------------------------------
-- lead_analysis
-- ---------------------------------------------------------------------------
CREATE TABLE lead_analysis (
    id                    uuid        PRIMARY KEY,
    organization_id       uuid        NOT NULL,
    lead_id               uuid        NOT NULL,
    analysis_type         text        NOT NULL,
    status                text        NOT NULL,
    score                 integer,
    score_version         text,
    -- A summarised failure reason, with no internal detail.
    failure_reason        text,
    requested_by_user_id  uuid        NOT NULL,
    created_at            timestamptz NOT NULL,
    started_at            timestamptz,
    completed_at          timestamptz,

    CONSTRAINT lead_analysis_org_id_unique UNIQUE (organization_id, id),
    CONSTRAINT lead_analysis_lead_same_org
        FOREIGN KEY (organization_id, lead_id) REFERENCES lead (organization_id, id),
    CONSTRAINT lead_analysis_requester_same_org
        FOREIGN KEY (organization_id, requested_by_user_id)
        REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT lead_analysis_type_valid CHECK (analysis_type IN ('DIGITAL_PRESENCE')),
    CONSTRAINT lead_analysis_status_valid CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT lead_analysis_score_range CHECK (score IS NULL OR (score >= 0 AND score <= 100)),
    -- A completed analysis has a reproducible score: with the formula version (doc 06 §7).
    CONSTRAINT lead_analysis_completed_scored CHECK (
        (status = 'COMPLETED') = (score IS NOT NULL AND score_version IS NOT NULL)),
    CONSTRAINT lead_analysis_finished_dated CHECK (
        (status IN ('COMPLETED', 'FAILED')) = (completed_at IS NOT NULL)),
    CONSTRAINT lead_analysis_failure_reason_length CHECK (failure_reason IS NULL OR length(failure_reason) <= 300)
);

-- One active analysis per lead: asking again while it runs returns the same one.
CREATE UNIQUE INDEX lead_analysis_one_active_per_lead
    ON lead_analysis (organization_id, lead_id)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX lead_analysis_by_lead ON lead_analysis (organization_id, lead_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- analysis_finding — one finding per criterion of the formula
-- ---------------------------------------------------------------------------
CREATE TABLE analysis_finding (
    id                   uuid    PRIMARY KEY,
    organization_id      uuid    NOT NULL,
    analysis_id          uuid    NOT NULL,
    criterion            text    NOT NULL,
    finding_status       text    NOT NULL,
    -- The state the verification produced, before any review. It preserves
    -- what the automation said even after the operator corrected it.
    original_status      text    NOT NULL,
    description          text    NOT NULL,
    confidence           text,
    points_awarded       integer NOT NULL DEFAULT 0,
    max_points           integer NOT NULL,
    review_status        text    NOT NULL,
    reviewed_by_user_id  uuid,
    reviewed_at          timestamptz,
    review_note          text,

    CONSTRAINT analysis_finding_org_id_unique UNIQUE (organization_id, id),
    CONSTRAINT analysis_finding_one_per_criterion UNIQUE (analysis_id, criterion),
    CONSTRAINT analysis_finding_analysis_same_org
        FOREIGN KEY (organization_id, analysis_id) REFERENCES lead_analysis (organization_id, id),
    CONSTRAINT analysis_finding_reviewer_same_org
        FOREIGN KEY (organization_id, reviewed_by_user_id)
        REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT analysis_finding_status_valid CHECK (finding_status IN (
        'CONFIRMED', 'NOT_FOUND', 'NOT_VERIFIED', 'NOT_APPLICABLE', 'CONTRADICTED')),
    CONSTRAINT analysis_finding_original_status_valid CHECK (original_status IN (
        'CONFIRMED', 'NOT_FOUND', 'NOT_VERIFIED', 'NOT_APPLICABLE', 'CONTRADICTED')),
    CONSTRAINT analysis_finding_confidence_valid CHECK (confidence IS NULL OR confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT analysis_finding_review_valid CHECK (review_status IN ('PENDING', 'CONFIRMED', 'CORRECTED')),
    CONSTRAINT analysis_finding_points_range CHECK (points_awarded >= 0 AND points_awarded <= max_points),
    -- Product rule 3: only a confirmed finding scores. The unknown does not penalise.
    CONSTRAINT analysis_finding_points_only_confirmed CHECK (finding_status = 'CONFIRMED' OR points_awarded = 0),
    CONSTRAINT analysis_finding_reviewed_consistent CHECK (
        (review_status = 'PENDING') = (reviewed_at IS NULL AND reviewed_by_user_id IS NULL)),
    CONSTRAINT analysis_finding_review_note_length CHECK (review_note IS NULL OR length(review_note) <= 500)
);

CREATE INDEX analysis_finding_by_analysis ON analysis_finding (organization_id, analysis_id);

-- ---------------------------------------------------------------------------
-- analysis_evidence — a short reference, never a copy of the page (docs/04 §2)
-- ---------------------------------------------------------------------------
CREATE TABLE analysis_evidence (
    id               uuid        PRIMARY KEY,
    organization_id  uuid        NOT NULL,
    finding_id       uuid        NOT NULL,
    evidence_type    text        NOT NULL,
    reference_url    text,
    description      text        NOT NULL,
    observed_at      timestamptz NOT NULL,
    captured_by      text        NOT NULL,
    -- A hash of the content observed: it proves what was seen without keeping the page.
    content_hash     varchar(64),

    CONSTRAINT analysis_evidence_finding_same_org
        FOREIGN KEY (organization_id, finding_id) REFERENCES analysis_finding (organization_id, id),
    CONSTRAINT analysis_evidence_type_valid CHECK (evidence_type IN (
        'HTTP_RESPONSE', 'HTML_SIGNAL', 'ROBOTS_TXT', 'COMPANY_RECORD', 'CONFIGURATION', 'OPERATOR_NOTE')),
    CONSTRAINT analysis_evidence_description_length CHECK (length(description) BETWEEN 1 AND 1000),
    CONSTRAINT analysis_evidence_hash_format CHECK (content_hash IS NULL OR content_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX analysis_evidence_by_finding ON analysis_evidence (organization_id, finding_id);
