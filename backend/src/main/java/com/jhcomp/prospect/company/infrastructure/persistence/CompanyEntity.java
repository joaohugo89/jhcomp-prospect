package com.jhcomp.prospect.company.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.jhcomp.prospect.shared.normalization.ContactNormalizer;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;
import com.jhcomp.prospect.shared.text.Texts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A company: the master record (RF-010, doc 06 §1).
 *
 * <p>A company can exist with no active opportunity. Company and Lead are not
 * synonyms.
 *
 * <p>The normalised columns are derived by {@link ContactNormalizer} inside
 * this class rather than by the caller: leaving the derivation outside would
 * allow a record to be written with divergent normalisation, and both
 * deduplication and contact blocking depend on it (decision D10).
 *
 * <p>Never exposed directly by the API (doc 03 §5).
 */
@Entity
@Table(name = "company")
public class CompanyEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "trade_name")
    private String tradeName;

    private String segment;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "normalized_domain")
    private String normalizedDomain;

    @Column(name = "instagram_url")
    private String instagramUrl;

    private String phone;

    @Column(name = "normalized_phone")
    private String normalizedPhone;

    @Column(name = "business_email")
    private String businessEmail;

    private String address;

    private String notes;

    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Who registered or imported it. Unchanged by a merge (D32). */
    @Column(name = "created_by_user_id", updatable = false)
    private UUID createdByUserId;

    /** The company that absorbed this one in a merge (D32). Written only by the merge, under a lock. */
    @Column(name = "merged_into_company_id", insertable = false, updatable = false)
    private UUID mergedIntoCompanyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanyEntity() {
        // Required by JPA.
    }

    public CompanyEntity(UUID id, UUID organizationId, UUID createdByUserId, CompanyData data, Instant now) {
        super(id);
        this.organizationId = organizationId;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
        apply(data);
    }

    /** A company's editable data. */
    public record CompanyData(
            String name,
            String tradeName,
            String segment,
            String city,
            String state,
            String websiteUrl,
            String instagramUrl,
            String phone,
            String businessEmail,
            String address,
            String notes) {}

    public void update(CompanyData data, Instant now) {
        apply(data);
        this.updatedAt = now;
    }

    private void apply(CompanyData data) {
        this.name = data.name().strip();
        this.tradeName = Texts.blankToNull(data.tradeName());
        this.segment = Texts.blankToNull(data.segment());
        this.city = data.city().strip();
        this.state = data.state().strip().toUpperCase(java.util.Locale.ROOT);
        this.websiteUrl = Texts.blankToNull(data.websiteUrl());
        this.instagramUrl = Texts.blankToNull(data.instagramUrl());
        this.phone = Texts.blankToNull(data.phone());
        this.businessEmail = ContactNormalizer.email(data.businessEmail()).orElse(null);
        this.address = Texts.blankToNull(data.address());
        this.notes = Texts.blankToNull(data.notes());

        // Derived. Null when the value given does not allow confident
        // normalisation; a guess would draw distinct records together.
        this.normalizedName = ContactNormalizer.name(this.name).orElseThrow(
                () -> new IllegalArgumentException("Company name cannot be normalized"));
        this.normalizedDomain = ContactNormalizer.domain(this.websiteUrl).orElse(null);
        this.normalizedPhone = ContactNormalizer.phone(this.phone).orElse(null);
    }

    /**
     * Fills only the optional fields that are empty.
     *
     * <p>It never overwrites an existing value (RF-023): data already on file
     * may have been confirmed by another source, and a spreadsheet has no way
     * of knowing. Name, city and state never change here: they identify the
     * company.
     *
     * @return the API keys of the fields filled, in form order
     */
    public List<String> fillBlanks(CompanyData incoming, Instant now) {
        List<String> filled = new ArrayList<>();
        CompanyData merged = new CompanyData(
                name,
                fill("tradeName", tradeName, incoming.tradeName(), filled),
                fill("segment", segment, incoming.segment(), filled),
                city,
                state,
                fill("websiteUrl", websiteUrl, incoming.websiteUrl(), filled),
                fill("instagramUrl", instagramUrl, incoming.instagramUrl(), filled),
                fill("phone", phone, incoming.phone(), filled),
                fill("businessEmail", businessEmail, incoming.businessEmail(), filled),
                fill("address", address, incoming.address(), filled),
                fill("notes", notes, incoming.notes(), filled));
        if (!filled.isEmpty()) {
            update(merged, now);
        }
        return List.copyOf(filled);
    }

    private static String fill(String key, String current, String incoming, List<String> filled) {
        if (current == null && incoming != null && !incoming.isBlank()) {
            filled.add(key);
            return incoming;
        }
        return current;
    }

    public void archive(Instant now) {
        if (this.archivedAt == null) {
            this.archivedAt = now;
            this.updatedAt = now;
        }
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public UUID getMergedIntoCompanyId() {
        return mergedIntoCompanyId;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public String getSegment() {
        return segment;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getNormalizedDomain() {
        return normalizedDomain;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public String getPhone() {
        return phone;
    }

    public String getNormalizedPhone() {
        return normalizedPhone;
    }

    public String getBusinessEmail() {
        return businessEmail;
    }

    public String getAddress() {
        return address;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
