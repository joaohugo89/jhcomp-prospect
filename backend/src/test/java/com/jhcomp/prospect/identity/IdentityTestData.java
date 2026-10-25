package com.jhcomp.prospect.identity;

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
 * Cria organizacoes, usuarios e associacoes para os testes de integracao.
 *
 * <p>Todos os dados sao ficticios e usam dominios reservados para teste
 * ({@code .test}, {@code example.test}): nunca dado pessoal real em fixture
 * (doc 10 §10).
 *
 * <p>Cada cenario usa identificadores unicos por execucao, para que os testes
 * nao dependam de ordem nem interfiram uns nos outros.
 */
@TestComponent
public class IdentityTestData {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public IdentityTestData(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationMemberRepository memberRepository,
            PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Dados de um membro criado, com a senha em claro para uso no login do teste. */
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
                userStatus));

        OrganizationMemberEntity member = memberRepository.save(new OrganizationMemberEntity(
                UUID.randomUUID(), organization, user, role, memberStatus));

        return new Member(member.getId(), user.getId(), organization.getId(), email, password);
    }

    /** Atalho para o caso mais comum: organizacao ativa com um administrador ativo. */
    @Transactional
    public Member createActiveAdmin(String slugPrefix) {
        return createMember(
                createOrganization(slugPrefix, OrganizationStatus.ACTIVE),
                OrganizationRole.ADMIN,
                UserStatus.ACTIVE,
                MemberStatus.ACTIVE);
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
