package com.jhcomp.prospect.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Cria a organizacao e o administrador iniciais em desenvolvimento local.
 *
 * <p>Restrito ao perfil {@code local} de proposito. Semear um usuario por
 * migration Flyway colocaria uma senha conhecida em todo ambiente que rodasse
 * as migrations, producao incluida — e migrations sao imutaveis, de modo que o
 * erro nao poderia ser simplesmente corrigido depois. Em producao, o
 * equivalente e o {@link AdminBootstrapCommand}, executado uma unica vez.
 *
 * <p>A senha vem de {@code LOCAL_ADMIN_PASSWORD} e nao tem valor padrao: um
 * default seria uma credencial conhecida publicamente.
 */
@Component
@Profile("local")
public class LocalBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalBootstrap.class);

    private final InitialSetupService setup;
    private final String adminEmail;
    private final String adminPassword;
    private final String organizationName;

    LocalBootstrap(
            InitialSetupService setup,
            @Value("${jhcomp.bootstrap.admin-email:admin@jhcomp.local}") String adminEmail,
            @Value("${jhcomp.bootstrap.admin-password:}") String adminPassword,
            @Value("${jhcomp.bootstrap.organization-name:JH.COMP}") String organizationName) {
        this.setup = setup;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.organizationName = organizationName;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = MembershipUserDetailsService.normalizeEmail(adminEmail);

        if (setup.userExists(email)) {
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

        setup.create(organizationName, "jhcomp", email, adminPassword);

        // Registra o e-mail, que o operador precisa para entrar, e nunca a senha.
        log.info("Bootstrap local: organizacao '{}' e administrador '{}' criados.", organizationName, email);
    }
}
