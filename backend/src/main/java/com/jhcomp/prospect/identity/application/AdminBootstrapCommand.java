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

import com.jhcomp.prospect.shared.normalization.ContactNormalizer;

/**
 * Cria o primeiro administrador de producao (D19, D27).
 *
 * <p>Comando de execucao unica: o perfil {@code bootstrap} sobe sem porta
 * publicada nem worker, roda este passo e termina. So funciona com o banco ainda
 * sem organizacao; rodar de novo e recusado. Falha
 * com excecao, e portanto com codigo de saida diferente de zero, em vez de
 * apenas registrar aviso: em producao, um bootstrap que nao fez nada precisa
 * ser percebido por quem o executou.
 */
@Component
@Profile("bootstrap")
@Order(0)
class AdminBootstrapCommand implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

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
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (setup.hasOrganization()) {
            throw new IllegalStateException("Database already has an organization: bootstrap runs only once");
        }

        setup.create(organizationName.strip(), slugOf(organizationName), email, adminPassword);
        log.info("Bootstrap de producao: organizacao '{}' e administrador '{}' criados.", organizationName, email);
    }

    static String slugOf(String name) {
        // Mesma normalizacao do resto do sistema: sem acento e em minusculas.
        String slug = NON_SLUG.matcher(ContactNormalizer.name(name).orElse("")).replaceAll("-")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "org" : slug;
    }
}
