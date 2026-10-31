package com.jhcomp.prospect.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Dados comerciais do fechamento (D2): servico, valor e motivo de perda.
 *
 * <p>Nao guarda etapa nem resultado: ganho ou perdido e o status do lead.
 * Guardar o resultado aqui recriaria duas fontes de estado, o que a D2 elimina.
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
        // Exigido pelo JPA.
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

    /** Motivo informado ao marcar como perdido, se o deal ainda nao tiver um. */
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
