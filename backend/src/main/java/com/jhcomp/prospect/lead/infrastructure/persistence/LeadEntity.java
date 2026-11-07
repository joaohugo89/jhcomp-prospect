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
 * A commercial opportunity attached to a company (RF-011, doc 06 §1).
 *
 * <p>{@code status} is the single source of commercial state (decision D2).
 * The valid transitions live in {@link LeadStatusTransitions}; this class only
 * applies them and keeps {@code closed_at} consistent with the state.
 */
@Entity
@Table(name = "lead")
public class LeadEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    /**
     * The company, read-only: the column is written through {@code companyId}.
     * It exists so the listing can filter and display without one query per
     * row.
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
        // Required by JPA.
    }

    /**
     * Opens an opportunity for the company.
     *
     * <p>It takes the company, not a loose id: the lead's organisation is
     * derived from it, so there is no way to build a lead of one organisation
     * pointing at a company of another (V4 refuses it in the database too).
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
     * Applies a state transition.
     *
     * @return {@code true} when the state changed; {@code false} when the
     *     state given was already the current one, in which case nothing is
     *     altered
     * @throws IllegalStateException when the transition is not allowed
     */
    public boolean changeStatus(LeadStatus newStatus, Instant now) {
        if (!LeadStatusTransitions.isAllowed(this.status, newStatus)) {
            throw new IllegalStateException("Transition not allowed");
        }

        if (this.status == newStatus) {
            return false;
        }

        this.status = newStatus;
        // closed_at follows the terminal state; the database has a CHECK for
        // the same rule, so any path that forgets it fails early.
        this.closedAt = newStatus.isTerminal() ? now : null;
        this.updatedAt = now;

        return true;
    }

    /** The score of the latest completed analysis, with the formula version (D3). */
    public void applyScore(int newScore, String version, Instant now) {
        this.score = newScore;
        this.scoreVersion = version;
        this.updatedAt = now;
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
