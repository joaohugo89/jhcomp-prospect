-- V4 — Tenant isolation enforced by the database (doc 04 §4).
--
-- In V3, lead.company_id referenced company(id) without organization_id, so
-- the database accepted a lead of organisation A pointing at a company of
-- organisation B. The application prevents it, but a single forgotten query
-- without a scope would be enough to cross tenants. Here every reference
-- between private entities includes organization_id, and a cross-tenant
-- association becomes an integrity error.
--
-- References to a user now point at organization_member: besides the user
-- existing, they have to be a member of the same organisation. A revoked
-- membership remains valid as a historical reference, because revocation
-- changes the status and does not delete the row.
--
-- It corrects V3 in a new version because V3 had already been applied
-- (migrations are immutable once applied).

-- The targets of the composite keys. id is already unique; the pair exists to be referenced.
ALTER TABLE company ADD CONSTRAINT company_org_id_unique UNIQUE (organization_id, id);
ALTER TABLE lead    ADD CONSTRAINT lead_org_id_unique    UNIQUE (organization_id, id);

-- lead ---------------------------------------------------------------------
ALTER TABLE lead
    DROP CONSTRAINT lead_company_id_fkey,
    DROP CONSTRAINT lead_assigned_user_id_fkey,
    ADD CONSTRAINT lead_company_same_org
        FOREIGN KEY (organization_id, company_id) REFERENCES company (organization_id, id),
    ADD CONSTRAINT lead_assigned_member_same_org
        FOREIGN KEY (organization_id, assigned_user_id)
        REFERENCES organization_member (organization_id, user_id);

-- lead_source --------------------------------------------------------------
-- lead_id and collected_by_user_id are optional: with MATCH SIMPLE, the
-- default, the composite key is only checked when the column is filled.
ALTER TABLE lead_source
    DROP CONSTRAINT lead_source_company_id_fkey,
    DROP CONSTRAINT lead_source_lead_id_fkey,
    DROP CONSTRAINT lead_source_collected_by_user_id_fkey,
    ADD CONSTRAINT lead_source_company_same_org
        FOREIGN KEY (organization_id, company_id) REFERENCES company (organization_id, id),
    ADD CONSTRAINT lead_source_lead_same_org
        FOREIGN KEY (organization_id, lead_id) REFERENCES lead (organization_id, id),
    ADD CONSTRAINT lead_source_collector_same_org
        FOREIGN KEY (organization_id, collected_by_user_id)
        REFERENCES organization_member (organization_id, user_id);

-- lead_status_change -------------------------------------------------------
ALTER TABLE lead_status_change
    DROP CONSTRAINT lead_status_change_lead_id_fkey,
    DROP CONSTRAINT lead_status_change_changed_by_user_id_fkey,
    ADD CONSTRAINT lead_status_change_lead_same_org
        FOREIGN KEY (organization_id, lead_id) REFERENCES lead (organization_id, id),
    ADD CONSTRAINT lead_status_change_actor_same_org
        FOREIGN KEY (organization_id, changed_by_user_id)
        REFERENCES organization_member (organization_id, user_id);
