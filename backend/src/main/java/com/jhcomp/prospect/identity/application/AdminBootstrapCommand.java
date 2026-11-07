package com.jhcomp.prospect.identity.application;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.jhcomp.prospect.identity.domain.PasswordPolicy;
import com.jhcomp.prospect.shared.normalization.ContactNormalizer;

/**
 * Creates the first administrator in production (D19, D27).
 *
 * <p>A single-use command: the {@code bootstrap} profile starts with no
 * published port and no worker, runs this step and exits. It only works while
 * the database still has no organisation; running it again is refused. It
 * fails with an exception, and therefore with a non-zero exit code, rather
 * than merely logging a warning: in production, a bootstrap that did nothing
 * has to be noticed by whoever ran it.
 */
@Component
@Profile("bootstrap")
@Order(0)
class AdminBootstrapCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapCommand.class);

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private final InitialSetupService setup;
    private final String adminEmail;
    private final String adminPassword;
    private final String organizationName;

    AdminBootstrapCommand(
            InitialSetupService setup,
            @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String adminEmail,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String adminPassword,
            @Value("${BOOTSTRAP_ORGANIZATION_NAME:JH.COMP}") String organizationName) {
        this.setup = setup;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.organizationName = organizationName;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = MembershipUserDetailsService.normalizeEmail(adminEmail);
        if (email.isBlank() || !email.contains("@")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL must be a valid e-mail");
        }
        // The same policy as the in-application password change (D28).
        PasswordPolicy.violation(adminPassword).ifPresent(violation -> {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD: " + violation);
        });
        if (setup.hasOrganization()) {
            throw new IllegalStateException("Database already has an organization: bootstrap runs only once");
        }

        setup.create(organizationName.strip(), slugOf(organizationName), email, adminPassword);
        log.info("Bootstrap de producao: organizacao '{}' e administrador '{}' criados.", organizationName, email);
    }

    static String slugOf(String name) {
        // The same normalisation as the rest of the system: unaccented and lower case.
        String slug = NON_SLUG.matcher(ContactNormalizer.name(name).orElse("")).replaceAll("-")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "org" : slug;
    }
}
