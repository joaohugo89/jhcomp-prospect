package com.jhcomp.prospect.discovery;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Uma importacao CSV e seus totais (RF-015, D23). */
@Entity
@Table(name = "import_batch")
public class ImportBatchEntity {

    /** Ciclo da importacao. */
    public enum Status {
        /** Analisada, aguardando confirmacao do operador. Nada foi gravado em company. */
        ANALYZED,
        /** Confirmacao em andamento; impede confirmar duas vezes. */
        IMPORTING,
        COMPLETED,
        /** Descartada sem importar; os valores das linhas foram apagados. */
        DISCARDED
    }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "file_sha256", nullable = false, updatable = false, length = 64)
    private String fileSha256;

    @Column(name = "source_name", nullable = false, updatable = false)
    private String sourceName;

    @Column(name = "dataset_note", updatable = false)
    private String datasetNote;

    @Column(name = "column_mapping", nullable = false, updatable = false)
    private String columnMapping;

    @Column(name = "ignored_columns", nullable = false, updatable = false)
    private String ignoredColumns;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "total_rows", nullable = false, updatable = false)
    private int totalRows;

    @Column(name = "valid_rows", nullable = false)
    private int validRows;

    @Column(name = "rejected_rows", nullable = false, updatable = false)
    private int rejectedRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "leads_created", nullable = false)
    private int leadsCreated;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportBatchEntity() {
        // Exigido pelo JPA.
    }

    public ImportBatchEntity(
            UUID id,
            UUID organizationId,
            String fileName,
            String fileSha256,
            String sourceName,
            String datasetNote,
            String columnMapping,
            String ignoredColumns,
            int validRows,
            int rejectedRows,
            int duplicateRows,
            UUID createdByUserId,
            Instant now) {
        this.id = id;
        this.organizationId = organizationId;
        this.fileName = fileName;
        this.fileSha256 = fileSha256;
        this.sourceName = sourceName;
        this.datasetNote = datasetNote;
        this.columnMapping = columnMapping;
        this.ignoredColumns = ignoredColumns;
        this.status = Status.ANALYZED;
        this.totalRows = validRows + rejectedRows + duplicateRows;
        this.validRows = validRows;
        this.rejectedRows = rejectedRows;
        this.duplicateRows = duplicateRows;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
    }

    /**
     * Registra o resultado da confirmacao.
     *
     * @param newlyDuplicated linhas validas na analise que, na confirmacao, ja
     *     tinham empresa correspondente gravada por outro caminho
     */
    void complete(int imported, int leads, int newlyDuplicated, Instant now) {
        this.validRows -= newlyDuplicated;
        this.duplicateRows += newlyDuplicated;
        this.importedRows = imported;
        this.leadsCreated = leads;
        this.status = Status.COMPLETED;
        this.completedAt = now;
    }

    void discard(Instant now) {
        this.status = Status.DISCARDED;
        this.completedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getDatasetNote() {
        return datasetNote;
    }

    public String getColumnMapping() {
        return columnMapping;
    }

    public String getIgnoredColumns() {
        return ignoredColumns;
    }

    public Status getStatus() {
        return status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getValidRows() {
        return validRows;
    }

    public int getRejectedRows() {
        return rejectedRows;
    }

    public int getDuplicateRows() {
        return duplicateRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public int getLeadsCreated() {
        return leadsCreated;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
