package com.jhcomp.prospect.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Associacao entre usuario e organizacao, com papel (RF-003).
 *
 * <p>E esta entidade, e nao o usuario, que define a organizacao ativa e o papel
 * usados na autorizacao.
 */
@Entity
@Table(name = "organization_member")
public class OrganizationMemberEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected OrganizationMemberEntity() {
        // Exigido pelo JPA.
    }

    public OrganizationMemberEntity(
            UUID id,
            OrganizationEntity organization,
            UserEntity user,
            OrganizationRole role,
            MemberStatus status) {
        this.id = id;
        this.organization = organization;
        this.user = user;
        this.role = role;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public OrganizationEntity getOrganization() {
        return organization;
    }

    public UserEntity getUser() {
        return user;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
