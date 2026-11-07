package com.jhcomp.prospect.identity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.domain.OrganizationStatus;
import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationRepository;
import com.jhcomp.prospect.identity.infrastructure.persistence.UserEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.UserRepository;

/**
 * Creates organisations, users and memberships for the integration tests.
 *
 * <p>Every value is fictitious and uses domains reserved for testing
 * ({@code .test}, {@code example.test}): never real personal data in a
 * fixture (doc 10 §10).
 *
 * <p>Each scenario uses identifiers unique per run, so the tests neither
 * depend on order nor interfere with one another.
 */
@TestComponent
public class IdentityTestData {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final jakarta.persistence.EntityManager entityManager;

    public IdentityTestData(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationMemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            jakarta.persistence.EntityManager entityManager) {
        this.entityManager = entityManager;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** The data of a created member, with the password in clear text for the test's login. */
    public record Member(UUID memberId, UUID userId, UUID organizationId, String email, String password) {}

    @Transactional
    public OrganizationEntity createOrganization(String slugPrefix, OrganizationStatus status) {
        String slug = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return organizationRepository.save(
                new OrganizationEntity(UUID.randomUUID(), "Org " + slug, slug, status));
    }

    @Transactional
    public Member createMember(
            OrganizationEntity organization,
            OrganizationRole role,
            UserStatus userStatus,
            MemberStatus memberStatus) {

        String email = "user-" + UUID.randomUUID().toString().substring(0, 12) + "@example.test";
        String password = "senha-de-teste-123";

        UserEntity user = userRepository.save(new UserEntity(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(password),
                "Usuario de Teste",
                userStatus,
                false,
                Instant.now()));

        OrganizationMemberEntity member = memberRepository.save(new OrganizationMemberEntity(
                UUID.randomUUID(), organization, user, role, memberStatus, Instant.now()));

        return new Member(member.getId(), user.getId(), organization.getId(), email, password);
    }

    /** One more active member in an organisation that already exists. */
    @Transactional
    public Member addMember(UUID organizationId, OrganizationRole role) {
        OrganizationEntity organization = organizationRepository.findById(organizationId).orElseThrow();
        return createMember(organization, role, UserStatus.ACTIVE, MemberStatus.ACTIVE);
    }

    /** A shortcut for the common case: an active organisation with an active administrator. */
    @Transactional
    public Member createActiveAdmin(String slugPrefix) {
        return createMember(
                createOrganization(slugPrefix, OrganizationStatus.ACTIVE),
                OrganizationRole.ADMIN,
                UserStatus.ACTIVE,
                MemberStatus.ACTIVE);
    }

    /**
     * Changes the persisted state directly, without going through the
     * application.
     *
     * <p>It uses SQL to simulate an administrative change made outside the
     * session in progress, which is exactly the scenario revalidation has to
     * detect.
     */
    @Transactional
    public void revokeMembership(UUID memberId) {
        entityManager
                .createNativeQuery("update organization_member set status = 'REVOKED' where id = :id")
                .setParameter("id", memberId)
                .executeUpdate();
    }

    @Transactional
    public void changeRole(UUID memberId, OrganizationRole role) {
        entityManager
                .createNativeQuery("update organization_member set role = :role where id = :id")
                .setParameter("role", role.name())
                .setParameter("id", memberId)
                .executeUpdate();
    }

    @Transactional
    public void disableUser(UUID userId) {
        entityManager
                .createNativeQuery("update app_user set status = 'DISABLED' where id = :id")
                .setParameter("id", userId)
                .executeUpdate();
    }

    @Transactional
    public void suspendOrganization(UUID organizationId) {
        entityManager
                .createNativeQuery("update organization set status = 'SUSPENDED' where id = :id")
                .setParameter("id", organizationId)
                .executeUpdate();
    }

    @Transactional
    public Member createActiveOperator(String slugPrefix) {
        return createMember(
                createOrganization(slugPrefix, OrganizationStatus.ACTIVE),
                OrganizationRole.OPERATOR,
                UserStatus.ACTIVE,
                MemberStatus.ACTIVE);
    }
}
