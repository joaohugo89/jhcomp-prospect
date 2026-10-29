package com.jhcomp.prospect.shared.audit;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_event")
public class AuditEventEntity extends AssignedIdEntity {

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditOutcome outcome;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "ip_address")
    private String ipAddress;

    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEventEntity() {
        // Exigido pelo JPA.
    }

    AuditEventEntity(
            UUID id,
            UUID organizationId,
            UUID actorUserId,
            AuditAction action,
            AuditOutcome outcome,
            String resourceType,
            String resourceId,
            String ipAddress,
            String detail,
            Instant occurredAt) {
        super(id);
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.outcome = outcome;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.ipAddress = ipAddress;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }
}
