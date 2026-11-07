package com.jhcomp.prospect.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
 * Creates the initial organisation and administrator (D19).
 *
 * <p>Used by the local bootstrap and by the production command. Never by a
 * migration: that would put a known password into every environment.
 */
@Service
class InitialSetupService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    InitialSetupService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationMemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    boolean hasOrganization() {
        return organizationRepository.count() > 0;
    }

    boolean userExists(String normalizedEmail) {
        return userRepository.existsByEmail(normalizedEmail);
    }

    /** Organisation, administrator and membership in one transaction: all three, or none. */
    @Transactional
    void create(String organizationName, String slug, String normalizedEmail, String rawPassword) {
        Instant now = clock.instant();
        OrganizationEntity organization = organizationRepository.save(
                new OrganizationEntity(UUID.randomUUID(), organizationName, slug, OrganizationStatus.ACTIVE));
        UserEntity admin = userRepository.save(new UserEntity(
                UUID.randomUUID(), normalizedEmail, passwordEncoder.encode(rawPassword), "Administrador", UserStatus.ACTIVE,
                false, now));
        memberRepository.save(new OrganizationMemberEntity(
                UUID.randomUUID(), organization, admin, OrganizationRole.ADMIN, MemberStatus.ACTIVE, now));
    }
}
