package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.domain.LeadStatusTransitions;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
public class LeadEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    /**
     * Empresa, somente leitura: a coluna e gravada por {@code companyId}. Existe
     * para filtrar e exibir a listagem sem uma consulta por linha.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", insertable = false, updatable = false)
    private CompanyEntity company;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected LeadEntity() {
        // Exigido pelo JPA.
    }

    /**
     * Abre uma oportunidade para a empresa.
     *
     * <p>Recebe a empresa, e nao seu id solto: a organizacao do lead e
     * derivada dela, e assim nao ha como montar um lead de uma organizacao
     * apontando para empresa de outra (a V4 tambem recusa no banco).
     */
    public LeadEntity(UUID id, CompanyEntity company, LeadPriority priority, Instant now) {
        super(id);
        this.organizationId = company.getOrganizationId();
        this.companyId = company.getId();
        this.company = company;
        this.status = LeadStatusTransitions.initial();
        this.priority = priority;
        this.createdAt = now;
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

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public CompanyEntity getCompany() {
        return company;
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
