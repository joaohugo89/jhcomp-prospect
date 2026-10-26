-- V4 — Isolamento de tenant garantido pelo banco (doc 04 §4).
--
-- Na V3, lead.company_id referenciava company(id) sem organization_id, entao o
-- banco aceitava um lead da organizacao A apontando para empresa da
-- organizacao B. A aplicacao impede, mas uma unica consulta esquecida sem
-- escopo bastaria para cruzar tenants. Aqui toda referencia entre entidades
-- privadas passa a incluir organization_id, e a associacao cruzada vira erro
-- de integridade.
--
-- Referencias a usuario passam a apontar para organization_member: alem de o
-- usuario existir, ele precisa ser membro da mesma organizacao. Associacao
-- revogada continua valida como referencia historica, porque a revogacao muda
-- o status e nao apaga a linha.
--
-- Corrige a V3 em nova versao porque a V3 ja foi aplicada (migrations sao
-- imutaveis apos aplicadas).

-- Alvos das chaves compostas. id ja e unico; o par existe para ser referenciado.
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
-- lead_id e collected_by_user_id sao opcionais: com MATCH SIMPLE, o padrao, a
-- chave composta so e verificada quando a coluna esta preenchida.
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
