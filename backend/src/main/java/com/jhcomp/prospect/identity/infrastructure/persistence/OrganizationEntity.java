package com.jhcomp.prospect.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.identity.domain.OrganizationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Organizacao. Nunca exposta diretamente na API (doc 03 §5).
 */
@Entity
@Table(name = "organization")
public class OrganizationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected OrganizationEntity() {
        // Exigido pelo JPA.
    }

    public OrganizationEntity(UUID id, String name, String slug, OrganizationStatus status) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
