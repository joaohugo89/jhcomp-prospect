-- V9 — CRM: deals, interactions and tasks (RF-040 to RF-043, D2, D25).
--
-- Pipeline stages have no table (D25): they map 1:1 onto lead.status (D2) and
-- live in code. A deal stores no outcome: won or lost is the lead's status,
-- the single source of commercial state.

-- ---------------------------------------------------------------------------
-- deal — the commercial facts of a closing
-- ---------------------------------------------------------------------------
CREATE TABLE deal (
    id               uuid          PRIMARY KEY,
    organization_id  uuid          NOT NULL,
    lead_id          uuid          NOT NULL,
    service          text,
    -- Money in NUMERIC, never floating point (product rule 10).
    estimated_value  numeric(12, 2),
    currency         varchar(3)    NOT NULL DEFAULT 'BRL',
    loss_reason      text,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,

    CONSTRAINT deal_org_id_unique UNIQUE (organization_id, id),
    -- One negotiation per lead in the MVP (D2).
    CONSTRAINT deal_one_per_lead UNIQUE (organization_id, lead_id),
    CONSTRAINT deal_lead_same_org FOREIGN KEY (organization_id, lead_id) REFERENCES lead (organization_id, id),
    CONSTRAINT deal_value_non_negative CHECK (estimated_value IS NULL OR estimated_value >= 0),
    CONSTRAINT deal_currency_valid CHECK (currency = 'BRL'),
    CONSTRAINT deal_service_length CHECK (service IS NULL OR length(service) <= 200),
    CONSTRAINT deal_loss_reason_length CHECK (loss_reason IS NULL OR length(loss_reason) <= 500)
);

-- ---------------------------------------------------------------------------
-- interaction — relationship events (RF-041)
-- ---------------------------------------------------------------------------
CREATE TABLE interaction (
    id                  uuid        PRIMARY KEY,
    organization_id     uuid        NOT NULL,
    lead_id             uuid        NOT NULL,
    interaction_type    text        NOT NULL,
    channel             text        NOT NULL,
    direction           text        NOT NULL,
    occurred_at         timestamptz NOT NULL,
    outcome             text,
    notes               text,
    created_by_user_id  uuid        NOT NULL,
    created_at          timestamptz NOT NULL,
    -- The timeline tiebreak, on the same sequence as statuses and origins (V6).
    recorded_seq        bigint      NOT NULL DEFAULT nextval('timeline_event_seq'),

    CONSTRAINT interaction_lead_same_org FOREIGN KEY (organization_id, lead_id) REFERENCES lead (organization_id, id),
    CONSTRAINT interaction_author_same_org
        FOREIGN KEY (organization_id, created_by_user_id) REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT interaction_type_valid CHECK (interaction_type IN (
        'CONTACT', 'REPLY', 'MEETING', 'PROPOSAL', 'DO_NOT_CONTACT_REQUEST', 'NOTE')),
    CONSTRAINT interaction_channel_valid CHECK (channel IN (
        'PHONE', 'WHATSAPP', 'EMAIL', 'INSTAGRAM', 'IN_PERSON', 'OTHER')),
    CONSTRAINT interaction_direction_valid CHECK (direction IN ('OUTBOUND', 'INBOUND', 'INTERNAL')),
    CONSTRAINT interaction_outcome_length CHECK (outcome IS NULL OR length(outcome) <= 200),
    CONSTRAINT interaction_notes_length CHECK (notes IS NULL OR length(notes) <= 2000)
);

CREATE INDEX interaction_by_lead ON interaction (organization_id, lead_id, occurred_at DESC);
CREATE INDEX interaction_by_org ON interaction (organization_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- task — the lead's tasks (RF-042)
-- ---------------------------------------------------------------------------
CREATE TABLE task (
    id                  uuid        PRIMARY KEY,
    organization_id     uuid        NOT NULL,
    lead_id             uuid        NOT NULL,
    title               text        NOT NULL,
    due_at              timestamptz,
    status              text        NOT NULL,
    assigned_user_id    uuid,
    created_by_user_id  uuid        NOT NULL,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    completed_at        timestamptz,

    CONSTRAINT task_lead_same_org FOREIGN KEY (organization_id, lead_id) REFERENCES lead (organization_id, id),
    CONSTRAINT task_assignee_same_org
        FOREIGN KEY (organization_id, assigned_user_id) REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT task_author_same_org
        FOREIGN KEY (organization_id, created_by_user_id) REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT task_status_valid CHECK (status IN ('OPEN', 'DONE')),
    CONSTRAINT task_title_length CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    CONSTRAINT task_completed_consistent CHECK ((status = 'DONE') = (completed_at IS NOT NULL))
);

CREATE INDEX task_by_lead ON task (organization_id, lead_id, status, due_at);
CREATE INDEX task_open_by_org ON task (organization_id, due_at) WHERE status = 'OPEN';
