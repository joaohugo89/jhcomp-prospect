package com.jhcomp.prospect.analysis.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.analysis.domain.AnalysisStatus;
import com.jhcomp.prospect.analysis.domain.ScoreFormula;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Analise de presenca digital de um lead (RF-030). */
@Entity
@Table(name = "lead_analysis")
public class LeadAnalysisEntity extends AssignedIdEntity {

    public static final String TYPE_DIGITAL_PRESENCE = "DIGITAL_PRESENCE";

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Column(name = "analysis_type", nullable = false, updatable = false)
    private String analysisType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    private Integer score;

    @Column(name = "score_version")
    private String scoreVersion;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private UUID requestedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected LeadAnalysisEntity() {
        // Exigido pelo JPA.
    }

    public LeadAnalysisEntity(UUID organizationId, UUID leadId, UUID requestedByUserId, Instant now) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.leadId = leadId;
        this.analysisType = TYPE_DIGITAL_PRESENCE;
        this.status = AnalysisStatus.PENDING;
        this.requestedByUserId = requestedByUserId;
        this.createdAt = now;
    }

    public void start(Instant now) {
        this.status = AnalysisStatus.PROCESSING;
        // Nova tentativa do job mantem o primeiro inicio.
        if (this.startedAt == null) {
            this.startedAt = now;
        }
    }

    public void complete(int score, Instant now) {
        this.status = AnalysisStatus.COMPLETED;
        this.score = score;
        this.scoreVersion = ScoreFormula.VERSION;
        this.completedAt = now;
    }

    /** Recalculo apos revisao de achado (D3): mesma formula, novos estados. */
    public void rescore(int score) {
        this.score = score;
        this.scoreVersion = ScoreFormula.VERSION;
    }

    public void fail(String reason, Instant now) {
        this.status = AnalysisStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = now;
    }

    public boolean isActive() {
        return status == AnalysisStatus.PENDING || status == AnalysisStatus.PROCESSING;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public String getAnalysisType() {
        return analysisType;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public Integer getScore() {
        return score;
    }

    public String getScoreVersion() {
        return scoreVersion;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
