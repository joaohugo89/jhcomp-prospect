package com.jhcomp.prospect.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Creates the initial organisation and administrator in local development.
 *
 * <p>Restricted to the {@code local} profile on purpose. Seeding a user
 * through a Flyway migration would put a known password into every
 * environment that ran the migrations, production included — and migrations
 * are immutable, so the mistake could not simply be corrected afterwards. In
 * production the equivalent is {@link AdminBootstrapCommand}, run exactly
 * once.
 *
 * <p>The password comes from {@code LOCAL_ADMIN_PASSWORD} and has no default:
 * a default would be a publicly known credential.
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

        // Logs the e-mail, which the operator needs in order to sign in, and never the password.
        log.info("Bootstrap local: organizacao '{}' e administrador '{}' criados.", organizationName, email);
    }
}
