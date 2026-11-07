-- V10 — Outreach: reviewed messages and the do-not-contact list (RF-050 to
-- RF-053, D26).
--
-- No column for automatic sending: the MVP only records a send made by the
-- operator (product rules 1 and 5).

-- ---------------------------------------------------------------------------
-- outreach_message — draft, approval and send record
-- ---------------------------------------------------------------------------
CREATE TABLE outreach_message (
    id                         uuid        PRIMARY KEY,
    organization_id            uuid        NOT NULL,
    lead_id                    uuid        NOT NULL,
    channel                    text        NOT NULL,
    subject                    text,
    body                       text        NOT NULL,
    -- The claims the AI flagged as unverified, one per line.
    unverified_claims          text,
    status                     text        NOT NULL,
    generated_by               text,
    prompt_version             text,
    created_by_user_id         uuid        NOT NULL,
    approved_by_user_id        uuid,
    approved_at                timestamptz,
    sent_at                    timestamptz,
    send_recorded_by_user_id   uuid,
    created_at                 timestamptz NOT NULL,
    updated_at                 timestamptz NOT NULL,

    CONSTRAINT outreach_message_lead_same_org FOREIGN KEY (organization_id, lead_id) REFERENCES lead (organization_id, id),
    CONSTRAINT outreach_message_author_same_org
        FOREIGN KEY (organization_id, created_by_user_id) REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT outreach_message_approver_same_org
        FOREIGN KEY (organization_id, approved_by_user_id) REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT outreach_message_sender_same_org
        FOREIGN KEY (organization_id, send_recorded_by_user_id) REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT outreach_message_channel_valid CHECK (channel IN ('WHATSAPP', 'EMAIL', 'INSTAGRAM', 'PHONE_CALL')),
    CONSTRAINT outreach_message_status_valid CHECK (status IN (
        'DRAFT', 'NEEDS_REVIEW', 'APPROVED', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT outreach_message_body_length CHECK (length(btrim(body)) BETWEEN 1 AND 4000),
    CONSTRAINT outreach_message_subject_length CHECK (subject IS NULL OR length(subject) <= 200),
    CONSTRAINT outreach_message_subject_email_only CHECK (subject IS NULL OR channel = 'EMAIL'),
    -- Approved, sent or failed always has an approval recorded.
    CONSTRAINT outreach_message_approval_consistent CHECK (
        (status IN ('APPROVED', 'SENT', 'FAILED')) = (approved_at IS NOT NULL AND approved_by_user_id IS NOT NULL)
        OR status = 'CANCELLED'),
    -- SENT and FAILED only with the operator's record (rule 5).
    CONSTRAINT outreach_message_send_consistent CHECK (
        (status IN ('SENT', 'FAILED')) = (sent_at IS NOT NULL AND send_recorded_by_user_id IS NOT NULL))
);

CREATE INDEX outreach_message_by_lead ON outreach_message (organization_id, lead_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- suppression_entry — the do-not-contact list (doc 07 §10)
-- ---------------------------------------------------------------------------
-- It keeps only the normalised identifier: the minimum needed to prevent
-- further contact. There is no removal in the MVP (D26).
CREATE TABLE suppression_entry (
    id                  uuid        PRIMARY KEY,
    organization_id     uuid        NOT NULL,
    identifier_type     text        NOT NULL,
    identifier          text        NOT NULL,
    -- Null: it applies to every channel.
    channel             text,
    reason              text        NOT NULL,
    requested_at        timestamptz NOT NULL,
    created_by_user_id  uuid        NOT NULL,
    created_at          timestamptz NOT NULL,

    CONSTRAINT suppression_entry_author_same_org
        FOREIGN KEY (organization_id, created_by_user_id) REFERENCES organization_member (organization_id, user_id),
    CONSTRAINT suppression_entry_type_valid CHECK (identifier_type IN ('PHONE', 'EMAIL', 'INSTAGRAM')),
    CONSTRAINT suppression_entry_channel_valid CHECK (channel IS NULL OR channel IN (
        'WHATSAPP', 'EMAIL', 'INSTAGRAM', 'PHONE_CALL')),
    CONSTRAINT suppression_entry_identifier_length CHECK (length(identifier) BETWEEN 1 AND 320),
    CONSTRAINT suppression_entry_reason_length CHECK (length(btrim(reason)) BETWEEN 1 AND 500)
);

-- One entry per identifier and channel scope.
CREATE UNIQUE INDEX suppression_entry_unique
    ON suppression_entry (organization_id, identifier_type, identifier, coalesce(channel, ''));
CREATE INDEX suppression_entry_by_org ON suppression_entry (organization_id, created_at DESC);
