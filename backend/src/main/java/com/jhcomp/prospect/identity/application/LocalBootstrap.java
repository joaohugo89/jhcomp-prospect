package com.jhcomp.prospect.identity.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
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
 * Cria a organizacao e o administrador iniciais em desenvolvimento local.
 *
 * <p>Restrito ao perfil {@code local} de proposito. Semear um usuario por
 * migration Flyway colocaria uma senha conhecida em todo ambiente que rodasse
 * as migrations, producao incluida — e migrations sao imutaveis, de modo que o
 * erro nao poderia ser simplesmente corrigido depois.
 *
 * <p>A senha vem de {@code LOCAL_ADMIN_PASSWORD} e nao tem valor padrao: um
 * default seria uma credencial conhecida publicamente.
 */
@Component
@Profile("local")
public class LocalBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalBootstrap.class);

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String organizationName;

    LocalBootstrap(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationMemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            @Value("${jhcomp.bootstrap.admin-email:admin@jhcomp.local}") String adminEmail,
            @Value("${jhcomp.bootstrap.admin-password:}") String adminPassword,
            @Value("${jhcomp.bootstrap.organization-name:JH.COMP}") String organizationName) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.organizationName = organizationName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = MembershipUserDetailsService.normalizeEmail(adminEmail);

        if (userRepository.existsByEmail(email)) {
            log.info("Bootstrap local: administrador ja existe, nada a fazer.");
            return;
        }

        if (adminPassword.isBlank()) {
            log.warn(
                    "Bootstrap local ignorado: defina LOCAL_ADMIN_PASSWORD para criar o administrador inicial.");
            return;
        }

        if (adminPassword.length() < 8) {
            log.warn("Bootstrap local ignorado: LOCAL_ADMIN_PASSWORD precisa de ao menos 8 caracteres.");
            return;
        }

        OrganizationEntity organization = organizationRepository.save(new OrganizationEntity(
                UUID.randomUUID(), organizationName, "jhcomp", OrganizationStatus.ACTIVE));

        UserEntity admin = userRepository.save(new UserEntity(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(adminPassword),
                "Administrador",
                UserStatus.ACTIVE));

        memberRepository.save(new OrganizationMemberEntity(
                UUID.randomUUID(), organization, admin, OrganizationRole.ADMIN, MemberStatus.ACTIVE));

        // Registra o e-mail, que o operador precisa para entrar, e nunca a senha.
        log.info("Bootstrap local: organizacao '{}' e administrador '{}' criados.", organizationName, email);
    }
}
