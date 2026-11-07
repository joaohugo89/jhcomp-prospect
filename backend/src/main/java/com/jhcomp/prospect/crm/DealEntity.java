package com.jhcomp.prospect.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The commercial facts of a closing (D2): service, value and loss reason.
 *
 * <p>It stores neither stage nor outcome: won or lost is the lead's status.
 * Storing the outcome here would recreate the two sources of state that D2
 * eliminates.
 */
@Entity
@Table(name = "deal")
public class DealEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    private String service;

    @Column(name = "estimated_value", precision = 12, scale = 2)
    private BigDecimal estimatedValue;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "loss_reason")
    private String lossReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DealEntity() {
        // Required by JPA.
    }

    DealEntity(UUID organizationId, UUID leadId, Instant now) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.leadId = leadId;
        this.currency = "BRL";
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String newService, BigDecimal newValue, String newLossReason, Instant now) {
        this.service = newService;
        this.estimatedValue = newValue;
        this.lossReason = newLossReason;
        this.updatedAt = now;
    }

    /** The reason given when marking it lost, if the deal has none yet. */
    void recordLossReasonIfAbsent(String reason, Instant now) {
        if (lossReason == null && reason != null) {
            this.lossReason = reason;
            this.updatedAt = now;
        }
    }

    public UUID getLeadId() {
        return leadId;
    }

    public String getService() {
        return service;
    }

    public BigDecimal getEstimatedValue() {
        return estimatedValue;
    }

    public String getCurrency() {
        return currency;
    }

    public String getLossReason() {
        return lossReason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
