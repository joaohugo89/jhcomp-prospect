package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Mudanca de estado do lead, com ator, data e estado anterior (doc 06 §2).
 *
 * <p>Alimenta o historico do RF-013. Registro acrescido, nunca alterado: nao ha
 * metodo de atualizacao nesta classe.
 */
@Entity
@Table(name = "lead_status_change")
public class LeadStatusChangeEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    /** Nulo na criacao do lead, quando nao havia estado anterior. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private LeadStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private LeadStatus toStatus;

    private String reason;

    @Column(name = "changed_by_user_id", nullable = false)
    private UUID changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected LeadStatusChangeEntity() {
        // Exigido pelo JPA.
    }

    public LeadStatusChangeEntity(
            UUID id,
            UUID organizationId,
            UUID leadId,
            LeadStatus fromStatus,
            LeadStatus toStatus,
            String reason,
            UUID changedByUserId,
            Instant changedAt) {
        super(id);
        this.organizationId = organizationId;
        this.leadId = leadId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedByUserId = changedByUserId;
        this.changedAt = changedAt;
    }
}
