package com.jhcomp.prospect.identity.application;

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
 * Cria organizacao e administrador iniciais (D19).
 *
 * <p>Usado pelo bootstrap local e pelo comando de producao. Nunca por
 * migration: isso colocaria uma senha conhecida em todo ambiente.
 */
@Service
class InitialSetupService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    InitialSetupService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationMemberRepository memberRepository,
            PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    boolean hasOrganization() {
        return organizationRepository.count() > 0;
    }

    boolean userExists(String normalizedEmail) {
        return userRepository.existsByEmail(normalizedEmail);
    }

    /** Organizacao, administrador e associacao na mesma transacao: ou os tres, ou nenhum. */
    @Transactional
    void create(String organizationName, String slug, String normalizedEmail, String rawPassword) {
        OrganizationEntity organization = organizationRepository.save(
                new OrganizationEntity(UUID.randomUUID(), organizationName, slug, OrganizationStatus.ACTIVE));
        UserEntity admin = userRepository.save(new UserEntity(
                UUID.randomUUID(), normalizedEmail, passwordEncoder.encode(rawPassword), "Administrador", UserStatus.ACTIVE));
        memberRepository.save(new OrganizationMemberEntity(
                UUID.randomUUID(), organization, admin, OrganizationRole.ADMIN, MemberStatus.ACTIVE));
    }
}
