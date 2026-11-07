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
 * A lead's state change, with the actor, the date and the previous state
 * (doc 06 §2).
 *
 * <p>It feeds the history of RF-013. An append-only record, never altered:
 * this class has no update method.
 */
@Entity
@Table(name = "lead_status_change")
public class LeadStatusChangeEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    /** Null when the lead was created, as there was no previous state. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private LeadStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private LeadStatus toStatus;

    @Column(name = "reason")
    private String reason;

    @Column(name = "changed_by_user_id", nullable = false)
    private UUID changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected LeadStatusChangeEntity() {
        // Required by JPA.
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

    public UUID getLeadId() {
        return leadId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
