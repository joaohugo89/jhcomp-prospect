package com.jhcomp.prospect.discovery;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/** Resultado de uma linha do arquivo (RF-015: erros por linha consultaveis). */
@Entity
@Table(name = "import_row")
public class ImportRowEntity implements Persistable<UUID> {

    public enum Status {
        /** Passou na validacao e nao parece duplicata. Importada ao confirmar. */
        VALID,
        /** Falhou na validacao; nunca e importada. */
        REJECTED,
        /** Possivel duplicata; so e importada se o operador escolher. */
        DUPLICATE,
        IMPORTED,
        /** Nao importada na confirmacao: duplicata nao escolhida. */
        SKIPPED
    }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "import_id", nullable = false, updatable = false)
    private UUID importId;

    @Column(name = "row_number", nullable = false, updatable = false)
    private int rowNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String name;

    @Column(name = "trade_name")
    private String tradeName;

    private String segment;
    private String city;
    private String state;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    private String phone;

    @Column(name = "business_email")
    private String businessEmail;

    private String address;
    private String notes;

    private String errors;

    @Column(name = "duplicate_of_company_id")
    private UUID duplicateOfCompanyId;

    @Column(name = "duplicate_of_row_number")
    private Integer duplicateOfRowNumber;

    @Column(name = "duplicate_signals")
    private String duplicateSignals;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "lead_id")
    private UUID leadId;

    /**
     * Linha recem-criada. Com id atribuido pela aplicacao, o Spring Data
     * trataria toda linha como existente e faria um SELECT antes de cada
     * INSERT: duas mil consultas a mais num arquivo no limite.
     */
    @Transient
    private boolean isNew = true;

    protected ImportRowEntity() {
        // Exigido pelo JPA.
    }

    ImportRowEntity(UUID organizationId, UUID importId, int rowNumber, Map<ImportField, String> values) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.importId = importId;
        this.rowNumber = rowNumber;
        this.status = Status.VALID;
        this.name = values.get(ImportField.NAME);
        this.tradeName = values.get(ImportField.TRADE_NAME);
        this.segment = values.get(ImportField.SEGMENT);
        this.city = values.get(ImportField.CITY);
        this.state = values.get(ImportField.STATE);
        this.websiteUrl = values.get(ImportField.WEBSITE_URL);
        this.instagramUrl = values.get(ImportField.INSTAGRAM_URL);
        this.phone = values.get(ImportField.PHONE);
        this.businessEmail = values.get(ImportField.BUSINESS_EMAIL);
        this.address = values.get(ImportField.ADDRESS);
        this.notes = values.get(ImportField.NOTES);
    }

    void reject(String errorsJson) {
        this.status = Status.REJECTED;
        this.errors = errorsJson;
    }

    void markDuplicate(UUID companyId, Integer rowNumberInFile, String signals) {
        this.status = Status.DUPLICATE;
        this.duplicateOfCompanyId = companyId;
        this.duplicateOfRowNumber = rowNumberInFile;
        this.duplicateSignals = signals;
    }

    void markImported(UUID createdCompanyId, UUID createdLeadId) {
        this.status = Status.IMPORTED;
        this.companyId = createdCompanyId;
        this.leadId = createdLeadId;
    }

    void markSkipped() {
        this.status = Status.SKIPPED;
    }

    /** Minimizacao: importacao descartada nao guarda os dados das empresas. */
    void clearValues() {
        this.name = null;
        this.tradeName = null;
        this.segment = null;
        this.city = null;
        this.state = null;
        this.websiteUrl = null;
        this.instagramUrl = null;
        this.phone = null;
        this.businessEmail = null;
        this.address = null;
        this.notes = null;
    }

    public Map<ImportField, String> values() {
        Map<ImportField, String> values = new EnumMap<>(ImportField.class);
        put(values, ImportField.NAME, name);
        put(values, ImportField.TRADE_NAME, tradeName);
        put(values, ImportField.SEGMENT, segment);
        put(values, ImportField.CITY, city);
        put(values, ImportField.STATE, state);
        put(values, ImportField.WEBSITE_URL, websiteUrl);
        put(values, ImportField.INSTAGRAM_URL, instagramUrl);
        put(values, ImportField.PHONE, phone);
        put(values, ImportField.BUSINESS_EMAIL, businessEmail);
        put(values, ImportField.ADDRESS, address);
        put(values, ImportField.NOTES, notes);
        return values;
    }

    private static void put(Map<ImportField, String> values, ImportField field, String value) {
        if (value != null) {
            values.put(field, value);
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.isNew = false;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public Status getStatus() {
        return status;
    }

    public String getErrors() {
        return errors;
    }

    public UUID getDuplicateOfCompanyId() {
        return duplicateOfCompanyId;
    }

    public Integer getDuplicateOfRowNumber() {
        return duplicateOfRowNumber;
    }

    public String getDuplicateSignals() {
        return duplicateSignals;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getLeadId() {
        return leadId;
    }
}
