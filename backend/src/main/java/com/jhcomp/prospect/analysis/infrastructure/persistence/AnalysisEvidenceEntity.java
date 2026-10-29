package com.jhcomp.prospect.analysis.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.analysis.domain.EvidenceType;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Evidencia de um achado: referencia e descricao curta, nunca copia da pagina. */
@Entity
@Table(name = "analysis_evidence")
public class AnalysisEvidenceEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "finding_id", nullable = false, updatable = false)
    private UUID findingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, updatable = false)
    private EvidenceType type;

    @Column(name = "reference_url", updatable = false)
    private String referenceUrl;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "captured_by", nullable = false, updatable = false)
    private String capturedBy;

    @Column(name = "content_hash", updatable = false, length = 64)
    private String contentHash;

    protected AnalysisEvidenceEntity() {
        // Exigido pelo JPA.
    }

    public AnalysisEvidenceEntity(
            UUID organizationId,
            UUID findingId,
            EvidenceType type,
            String referenceUrl,
            String description,
            Instant observedAt,
            String capturedBy,
            String contentHash) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.findingId = findingId;
        this.type = type;
        this.referenceUrl = referenceUrl;
        this.description = description;
        this.observedAt = observedAt;
        this.capturedBy = capturedBy;
        this.contentHash = contentHash;
    }

    public UUID getFindingId() {
        return findingId;
    }

    public EvidenceType getType() {
        return type;
    }

    public String getReferenceUrl() {
        return referenceUrl;
    }

    public String getDescription() {
        return description;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public String getCapturedBy() {
        return capturedBy;
    }

    public String getContentHash() {
        return contentHash;
    }
}
