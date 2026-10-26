package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.domain.LeadStatusTransitions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Oportunidade comercial associada a uma empresa (RF-011, doc 06 §1).
 *
 * <p>{@code status} e a fonte unica do estado comercial (decisao D2). As
 * transicoes validas vivem em {@link LeadStatusTransitions}; esta classe apenas
 * as aplica e mantem {@code closed_at} coerente com o estado.
 */
@Entity
@Table(name = "lead")
public class LeadEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadPriority priority;

    private Integer score;

    @Column(name = "score_version")
    private String scoreVersion;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected LeadEntity() {
        // Exigido pelo JPA.
    }

    public LeadEntity(UUID id, UUID organizationId, UUID companyId, LeadPriority priority, Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.companyId = companyId;
        this.status = LeadStatusTransitions.initial();
        this.priority = priority;
        this.updatedAt = now;
    }

    /**
     * Aplica uma transicao de estado.
     *
     * @return {@code true} se o estado mudou; {@code false} se o estado
     *     informado era o atual, caso em que nada e alterado
     * @throws IllegalStateException se a transicao nao for permitida
     */
    public boolean changeStatus(LeadStatus newStatus, Instant now) {
        if (!LeadStatusTransitions.isAllowed(this.status, newStatus)) {
            throw new IllegalStateException("Transition not allowed");
        }

        if (this.status == newStatus) {
            return false;
        }

        this.status = newStatus;
        // closed_at acompanha o estado terminal; o banco tem CHECK para a
        // mesma regra, de modo que um caminho que esqueca disso falha cedo.
        this.closedAt = newStatus.isTerminal() ? now : null;
        this.updatedAt = now;

        return true;
    }

    public void changePriority(LeadPriority newPriority, Instant now) {
        this.priority = newPriority;
        this.updatedAt = now;
    }

    public void assignTo(UUID userId, Instant now) {
        this.assignedUserId = userId;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public LeadPriority getPriority() {
        return priority;
    }

    public Integer getScore() {
        return score;
    }

    public String getScoreVersion() {
        return scoreVersion;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
