package com.jhcomp.prospect.company.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;
import com.jhcomp.prospect.shared.text.Texts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Proveniencia: de onde veio um dado e quando foi coletado (RF-020, RF-022).
 *
 * <p>Fica no modulo de empresa porque descreve a origem do dado da empresa
 * ({@code company_id} obrigatorio, D6); {@code lead_id} e preenchido quando a
 * origem tambem justifica uma oportunidade.
 *
 * <p>Registro acrescido, nunca alterado: nao ha metodo de atualizacao.
 */
@Entity
@Table(name = "lead_source")
public class LeadSourceEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "lead_id", updatable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false)
    private SourceType sourceType;

    @Column(name = "source_name", nullable = false, updatable = false)
    private String sourceName;

    @Column(name = "source_reference", updatable = false)
    private String sourceReference;

    @Column(name = "source_url", updatable = false)
    private String sourceUrl;

    @Column(name = "collected_at", nullable = false, updatable = false)
    private Instant collectedAt;

    @Column(name = "collected_by_user_id", updatable = false)
    private UUID collectedByUserId;

    @Column(name = "terms_or_dataset_note", updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LeadSourceEntity() {
        // Exigido pelo JPA.
    }

    public LeadSourceEntity(
            UUID id,
            UUID organizationId,
            UUID companyId,
            UUID leadId,
            SourceData data,
            UUID collectedByUserId,
            Instant now) {
        super(id);
        this.createdAt = now;
        this.organizationId = organizationId;
        this.companyId = companyId;
        this.leadId = leadId;
        this.sourceType = data.type();
        this.sourceName = data.name().strip();
        this.sourceReference = Texts.blankToNull(data.reference());
        this.sourceUrl = Texts.blankToNull(data.url());
        this.collectedAt = data.collectedAt();
        this.note = Texts.blankToNull(data.note());
        this.collectedByUserId = collectedByUserId;
    }

    /** Dados de uma origem, como informados pelo operador ou pela importacao. */
    public record SourceData(
            SourceType type, String name, String reference, String url, Instant collectedAt, String note) {}

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Instant getCollectedAt() {
        return collectedAt;
    }

    public UUID getCollectedByUserId() {
        return collectedByUserId;
    }

    public String getNote() {
        return note;
    }
}
