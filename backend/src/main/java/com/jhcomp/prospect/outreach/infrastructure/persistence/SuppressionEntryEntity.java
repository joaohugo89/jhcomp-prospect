package com.jhcomp.prospect.outreach.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.outreach.domain.ContactIdentifier;
import com.jhcomp.prospect.outreach.domain.IdentifierType;
import com.jhcomp.prospect.outreach.domain.OutreachChannel;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * An entry in the do-not-contact list (RF-053, doc 07 §10).
 *
 * <p>Immutable: there is no removal and no editing in the MVP (doc 06 §9,
 * D26).
 */
@Entity
@Table(name = "suppression_entry")
public class SuppressionEntryEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, updatable = false)
    private IdentifierType identifierType;

    @Column(nullable = false, updatable = false)
    private String identifier;

    /** Null: it applies to every channel. */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private OutreachChannel channel;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SuppressionEntryEntity() {
        // Required by JPA.
    }

    public SuppressionEntryEntity(
            UUID organizationId,
            ContactIdentifier identifier,
            OutreachChannel channel,
            String reason,
            Instant requestedAt,
            UUID createdByUserId,
            Instant now) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.identifierType = identifier.type();
        this.identifier = identifier.value();
        this.channel = channel;
        this.reason = reason;
        this.requestedAt = requestedAt;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
    }

    public IdentifierType getIdentifierType() {
        return identifierType;
    }

    public String getIdentifier() {
        return identifier;
    }

    /** Null: it applies to every channel. */
    public OutreachChannel getChannel() {
        return channel;
    }

    public String getReason() {
        return reason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
