package com.jhcomp.prospect.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The membership between a user and an organisation, with a role (RF-003).
 *
 * <p>It is this entity, not the user, that defines the active organisation
 * and the role used by authorisation.
 */
@Entity
@Table(name = "organization_member")
public class OrganizationMemberEntity extends AssignedIdEntity {

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrganizationMemberEntity() {
        // Required by JPA.
    }

    public OrganizationMemberEntity(
            UUID id,
            OrganizationEntity organization,
            UserEntity user,
            OrganizationRole role,
            MemberStatus status,
            Instant now) {
        super(id);
        this.organization = organization;
        this.user = user;
        this.role = role;
        this.status = status;
        this.createdAt = now;
    }

    /** Takes effect on the member's next request, through session revalidation (D21). */
    public void changeRole(OrganizationRole newRole) {
        this.role = newRole;
    }

    /** Revoking ends the member's session on their next request (D21). */
    public void changeStatus(MemberStatus newStatus) {
        this.status = newStatus;
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
