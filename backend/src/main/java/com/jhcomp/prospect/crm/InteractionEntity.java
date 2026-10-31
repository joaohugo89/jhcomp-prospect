package com.jhcomp.prospect.crm;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Evento de relacionamento com o lead (RF-041). Registro acrescido, nunca alterado. */
@Entity
@Table(name = "interaction")
public class InteractionEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, updatable = false)
    private InteractionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private InteractionChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private InteractionDirection direction;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(updatable = false)
    private String outcome;

    @Column(updatable = false)
    private String notes;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InteractionEntity() {
        // Exigido pelo JPA.
    }

    InteractionEntity(
            UUID organizationId,
            UUID leadId,
            InteractionType type,
            InteractionChannel channel,
            InteractionDirection direction,
            Instant occurredAt,
            String outcome,
            String notes,
            UUID createdByUserId,
            Instant now) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.leadId = leadId;
        this.type = type;
        this.channel = channel;
        this.direction = direction;
        this.occurredAt = occurredAt;
        this.outcome = outcome;
        this.notes = notes;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public InteractionType getType() {
        return type;
    }

    public InteractionChannel getChannel() {
        return channel;
    }

    public InteractionDirection getDirection() {
        return direction;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }
}
