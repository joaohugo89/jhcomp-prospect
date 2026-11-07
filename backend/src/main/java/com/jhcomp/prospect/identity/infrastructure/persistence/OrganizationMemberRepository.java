package com.jhcomp.prospect.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jhcomp.prospect.identity.domain.MemberStatus;

/**
 * Member queries.
 *
 * <p>Every query that returns private data takes {@code organizationId}, and
 * takes it mandatorily (RNF-001, doc 04 §4). There is deliberately no free
 * {@code findById(UUID)} here: looking something up by id without an
 * organisation scope is exactly what allows cross-organisation access, even
 * when the caller holds a valid UUID.
 */
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMemberEntity, UUID> {

    @Query("""
            select m from OrganizationMemberEntity m
            join fetch m.user u
            where m.organization.id = :organizationId
              and m.status = :status
            order by m.createdAt asc
            """)
    Page<OrganizationMemberEntity> findByOrganization(
            @Param("organizationId") UUID organizationId,
            @Param("status") MemberStatus status,
            Pageable pageable);

    /** A scoped lookup: an id from another organisation yields empty, not access. */
    @Query("""
            select m from OrganizationMemberEntity m
            join fetch m.user u
            where m.id = :memberId
              and m.organization.id = :organizationId
            """)
    Optional<OrganizationMemberEntity> findByIdAndOrganization(
            @Param("memberId") UUID memberId,
            @Param("organizationId") UUID organizationId);

    /**
     * The active membership used to resolve the organisation at login.
     *
     * <p>It requires an active organisation: an active member of a suspended
     * organisation does not authenticate.
     */
    @Query("""
            select m from OrganizationMemberEntity m
            join fetch m.organization o
            join fetch m.user u
            where u.email = :email
              and m.status = com.jhcomp.prospect.identity.domain.MemberStatus.ACTIVE
              and o.status = com.jhcomp.prospect.identity.domain.OrganizationStatus.ACTIVE
            """)
    java.util.List<OrganizationMemberEntity> findActiveMembershipsByEmail(@Param("email") String email);

    /**
     * A user's active membership, used to revalidate the session.
     *
     * <p>It requires an active user, an active membership and an active
     * organisation. If any of the three stops holding, the query returns empty
     * and the session must be ended.
     *
     * <p>It returns a list rather than an Optional: two active memberships
     * would make the organisation ambiguous, and the caller needs to be able
     * to refuse that case instead of receiving an exception.
     */
    @Query("""
            select m from OrganizationMemberEntity m
            join fetch m.organization o
            join fetch m.user u
            where u.id = :userId
              and m.status = com.jhcomp.prospect.identity.domain.MemberStatus.ACTIVE
              and o.status = com.jhcomp.prospect.identity.domain.OrganizationStatus.ACTIVE
              and u.status = com.jhcomp.prospect.identity.domain.UserStatus.ACTIVE
            """)
    java.util.List<OrganizationMemberEntity> findActiveMembershipsByUserId(@Param("userId") UUID userId);

    /**
     * The user is an active member of the organisation, with an active
     * account.
     *
     * <p>Used to validate assignment: a user UUID coming from the client is no
     * proof that they belong to the caller's organisation (doc 04 §4).
     */
    @Query("""
            select count(m) > 0 from OrganizationMemberEntity m
            where m.organization.id = :organizationId
              and m.user.id = :userId
              and m.status = com.jhcomp.prospect.identity.domain.MemberStatus.ACTIVE
              and m.user.status = com.jhcomp.prospect.identity.domain.UserStatus.ACTIVE
            """)
    boolean existsActiveMember(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

    /**
     * Members for the administration screen: in the requested states and, with
     * {@code search} (a LIKE pattern already lower-cased), by name or
     * e-mail.
     */
    @Query("""
            select m from OrganizationMemberEntity m
            join fetch m.user u
            where m.organization.id = :organizationId
              and m.status in :statuses
              and (lower(u.displayName) like :search escape '\\' or lower(u.email) like :search escape '\\')
            order by m.createdAt asc
            """)
    Page<OrganizationMemberEntity> search(
            @Param("organizationId") UUID organizationId,
            @Param("statuses") java.util.Collection<MemberStatus> statuses,
            @Param("search") String search,
            Pageable pageable);

    /** The team overview in one query: active, administrators, revoked, and temporary password pending. */
    @Query("""
            select count(case when m.status = com.jhcomp.prospect.identity.domain.MemberStatus.ACTIVE then 1 end),
                   count(case when m.status = com.jhcomp.prospect.identity.domain.MemberStatus.ACTIVE
                              and m.role = com.jhcomp.prospect.identity.domain.OrganizationRole.ADMIN then 1 end),
                   count(case when m.status = com.jhcomp.prospect.identity.domain.MemberStatus.REVOKED then 1 end),
                   count(case when m.status = com.jhcomp.prospect.identity.domain.MemberStatus.ACTIVE
                              and m.user.passwordChangeRequired = true then 1 end)
              from OrganizationMemberEntity m
             where m.organization.id = :organizationId
            """)
    java.util.List<Object[]> summarize(@Param("organizationId") UUID organizationId);

    /** Active administrators of the organisation, with active accounts. */
    @Query("""
            select count(m) from OrganizationMemberEntity m
            where m.organization.id = :organizationId
              and m.role = com.jhcomp.prospect.identity.domain.OrganizationRole.ADMIN
              and m.status = com.jhcomp.prospect.identity.domain.MemberStatus.ACTIVE
              and m.user.status = com.jhcomp.prospect.identity.domain.UserStatus.ACTIVE
            """)
    long countActiveAdmins(@Param("organizationId") UUID organizationId);

    /** The user's memberships in organisations other than the one given, in any state. */
    @Query("""
            select count(m) > 0 from OrganizationMemberEntity m
            where m.user.id = :userId and m.organization.id <> :organizationId
            """)
    boolean existsInOtherOrganization(@Param("userId") UUID userId, @Param("organizationId") UUID organizationId);
}
