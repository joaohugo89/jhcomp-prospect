package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Visao salva da lista de leads: nome e query string dos filtros (D31). */
@Entity
@Table(name = "lead_view")
public class LeadViewEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "query", nullable = false, length = 500)
    private String query;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LeadViewEntity() {
        // Exigido pelo JPA.
    }

    public LeadViewEntity(UUID id, UUID organizationId, UUID userId, String name, String query, Instant now) {
        super(id);
        this.organizationId = organizationId;
        this.userId = userId;
        this.name = name;
        this.query = query;
        this.createdAt = now;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getQuery() {
        return query;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
