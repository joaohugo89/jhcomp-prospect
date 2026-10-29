package com.jhcomp.prospect.analysis.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.analysis.domain.Confidence;
import com.jhcomp.prospect.analysis.domain.Criterion;
import com.jhcomp.prospect.analysis.domain.FindingStatus;
import com.jhcomp.prospect.analysis.domain.ReviewStatus;
import com.jhcomp.prospect.analysis.domain.ScoreFormula;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Achado de um criterio (RF-031).
 *
 * <p>Os pontos sao sempre derivados do estado pela {@link ScoreFormula}, nunca
 * atribuidos de fora: assim nao ha como gravar pontos sem confirmacao.
 */
@Entity
@Table(name = "analysis_finding")
public class AnalysisFindingEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "analysis_id", nullable = false, updatable = false)
    private UUID analysisId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Criterion criterion;

    @Enumerated(EnumType.STRING)
    @Column(name = "finding_status", nullable = false)
    private FindingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_status", nullable = false, updatable = false)
    private FindingStatus originalStatus;

    @Column(nullable = false, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    private Confidence confidence;

    @Column(name = "points_awarded", nullable = false)
    private int pointsAwarded;

    @Column(name = "max_points", nullable = false, updatable = false)
    private int maxPoints;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    protected AnalysisFindingEntity() {
        // Exigido pelo JPA.
    }

    public AnalysisFindingEntity(
            UUID organizationId,
            UUID analysisId,
            Criterion criterion,
            FindingStatus status,
            String description,
            Confidence confidence) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.analysisId = analysisId;
        this.criterion = criterion;
        this.status = status;
        this.originalStatus = status;
        this.description = description;
        this.confidence = confidence;
        this.maxPoints = criterion.maxPoints();
        this.pointsAwarded = ScoreFormula.pointsFor(criterion, status);
        this.reviewStatus = ReviewStatus.PENDING;
    }

    /**
     * Revisao humana (RF-033): mantem ou troca o estado e registra quem e quando.
     * O estado original da verificacao continua gravado.
     */
    public void review(FindingStatus newStatus, String note, UUID reviewerId, Instant now) {
        this.reviewStatus = newStatus == this.status ? ReviewStatus.CONFIRMED : ReviewStatus.CORRECTED;
        this.status = newStatus;
        this.pointsAwarded = ScoreFormula.pointsFor(criterion, newStatus);
        this.reviewNote = note;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public Criterion getCriterion() {
        return criterion;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public FindingStatus getOriginalStatus() {
        return originalStatus;
    }

    public String getDescription() {
        return description;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public UUID getReviewedByUserId() {
        return reviewedByUserId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }
}
