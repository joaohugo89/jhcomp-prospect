package com.jhcomp.prospect.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.domain.OrganizationStatus;
import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditEventRepository;
import com.jhcomp.prospect.shared.audit.AuditOutcome;

/**
 * Autenticacao por sessao (RF-001, RF-002, decisao D1).
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Autenticacao")
class AuthControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private org.springframework.security.web.csrf.CsrfTokenRepository csrfTokenRepository;

    private static String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    @Test
    @DisplayName("credenciais validas autenticam e resolvem a organizacao")
    void loginSucceeds() throws Exception {
        var admin = testData.createActiveAdmin("login-ok");

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.email(), admin.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(admin.userId().toString()))
                .andExpect(jsonPath("$.organizationId").value(admin.organizationId().toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                // O hash de senha nunca aparece na resposta.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("login bem-sucedido e registrado na auditoria")
    void loginIsAudited() throws Exception {
        var admin = testData.createActiveAdmin("login-audit");

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.email(), admin.password())))
                .andExpect(status().isOk());

        var events = auditRepository.findByOrganizationIdOrderByOccurredAtDesc(admin.organizationId());

        assertThat(events).isNotEmpty();
        assertThat(events.getFirst().getAction()).isEqualTo(AuditAction.LOGIN);
        assertThat(events.getFirst().getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(events.getFirst().getActorUserId()).isEqualTo(admin.userId());
    }

    /**
     * O critério de aceite do RF-001 exige que a recusa nao revele se o e-mail
     * existe. Testar status igual nao basta: o corpo tambem precisa ser
     * indistinguivel, senao a diferenca permite enumerar contas.
     */
    @Nested
    @DisplayName("falha de login nao revela a causa")
    class FailuresAreIndistinguishable {

        private String responseBodyFor(String email, String password) throws Exception {
            return mockMvc.perform(post("/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, password)))
                    .andExpect(status().isUnauthorized())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        }

        @Test
        @DisplayName("e-mail inexistente, senha errada, usuario desabilitado e organizacao suspensa respondem igual")
        void allFailuresLookTheSame() throws Exception {
            var validUser = testData.createActiveAdmin("fail-a");

            var disabledUser = testData.createMember(
                    testData.createOrganization("fail-b", OrganizationStatus.ACTIVE),
                    OrganizationRole.ADMIN,
                    UserStatus.DISABLED,
                    MemberStatus.ACTIVE);

            var suspendedOrgUser = testData.createMember(
                    testData.createOrganization("fail-c", OrganizationStatus.SUSPENDED),
                    OrganizationRole.ADMIN,
                    UserStatus.ACTIVE,
                    MemberStatus.ACTIVE);

            var revokedMember = testData.createMember(
                    testData.createOrganization("fail-d", OrganizationStatus.ACTIVE),
                    OrganizationRole.ADMIN,
                    UserStatus.ACTIVE,
                    MemberStatus.REVOKED);

            String unknownEmail = responseBodyFor("nao-existe@example.test", "senha-qualquer-123");
            String wrongPassword = responseBodyFor(validUser.email(), "senha-errada-123");
            String disabled = responseBodyFor(disabledUser.email(), disabledUser.password());
            String suspendedOrg = responseBodyFor(suspendedOrgUser.email(), suspendedOrgUser.password());
            String revoked = responseBodyFor(revokedMember.email(), revokedMember.password());

            assertThat(wrongPassword).isEqualTo(unknownEmail);
            assertThat(disabled).isEqualTo(unknownEmail);
            assertThat(suspendedOrg).isEqualTo(unknownEmail);
            assertThat(revoked).isEqualTo(unknownEmail);

            // E o corpo nao deve mencionar usuario, e-mail ou organizacao.
            assertThat(unknownEmail)
                    .contains("INVALID_CREDENTIALS")
                    .doesNotContain("disabled", "suspended", "not found", "revoked");
        }
    }

    @Test
    @DisplayName("falha de login e registrada sem guardar o e-mail tentado")
    void loginFailureIsAuditedWithoutEmail() throws Exception {
        String attemptedEmail = "nao-cadastrado-" + java.util.UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(attemptedEmail, "senha-qualquer-123")))
                .andExpect(status().isUnauthorized());

        var failures = auditRepository.findByActionOrderByOccurredAtDesc(AuditAction.LOGIN).stream()
                .filter(event -> event.getOutcome() == AuditOutcome.FAILURE)
                .toList();

        assertThat(failures).isNotEmpty();
        // O e-mail tentado seria dado pessoal de quem talvez nem seja usuario,
        // coletado sem finalidade necessaria (doc 07 §2).
        assertThat(failures).allSatisfy(event -> {
            assertThat(event.getDetail()).doesNotContain(attemptedEmail);
            assertThat(event.getActorUserId()).isNull();
        });
    }

    @Test
    @DisplayName("logout e registrado na auditoria")
    void logoutIsAudited() throws Exception {
        var admin = testData.createActiveAdmin("logout-audit");

        var session = (MockHttpSession) mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.email(), admin.password())))
                .andReturn()
                .getRequest()
                .getSession();

        mockMvc.perform(post("/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        var events = auditRepository.findByOrganizationIdOrderByOccurredAtDesc(admin.organizationId());

        assertThat(events).anySatisfy(event -> {
            assertThat(event.getAction()).isEqualTo(AuditAction.LOGOUT);
            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
            assertThat(event.getActorUserId()).isEqualTo(admin.userId());
        });
    }

    @Test
    @DisplayName("e-mail invalido falha na validacao com erro por campo")
    void invalidEmailFailsValidation() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nao-e-email", "senha-qualquer-123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                // MockMvc nao aplica o context-path, por isso aqui aparece sem
                // o prefixo /api/v1 que o servidor real inclui.
                .andExpect(jsonPath("$.instance").value("/auth/login"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    @DisplayName("senha curta falha na validacao sem chegar ao banco")
    void shortPasswordFailsValidation() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alguem@example.test", "curta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @DisplayName("o cookie CSRF e legivel pela pagina servida na raiz")
    void csrfCookieIsReadableFromRoot() {
        // Exercita o repositorio diretamente, e nao via requisicao HTTP: o
        // cookie so e reemitido quando o token e novo, o que tornaria a
        // assercao dependente da ordem dos testes.
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();

        var token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);

        var cookie = response.getCookie("XSRF-TOKEN");

        assertThat(cookie).describedAs("cookie XSRF-TOKEN").isNotNull();
        // Sem path "/", o cookie herdaria o context-path /api/v1 e o
        // JavaScript da pagina, servida em /, nao o veria: o login falharia
        // com 403 no navegador, embora passasse em teste de API.
        assertThat(cookie.getPath()).isEqualTo("/");
        // Este cookie precisa ser legivel por JavaScript, ao contrario do de sessao.
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    @DisplayName("login sem token CSRF e recusado")
    void loginRequiresCsrfToken() throws Exception {
        var admin = testData.createActiveAdmin("csrf");

        // Sem .with(csrf()). Com autenticacao por cookie, aceitar POST sem
        // token permitiria requisicao forjada a partir de outro site.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.email(), admin.password())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("excesso de tentativas resulta em 429")
    void tooManyAttemptsAreBlocked() throws Exception {
        var admin = testData.createActiveAdmin("rate-limit");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(admin.email(), "senha-errada-123")))
                    .andExpect(status().isUnauthorized());
        }

        // A senha correta tambem e recusada enquanto o bloqueio durar.
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.email(), admin.password())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("/auth/me exige autenticacao")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("/auth/me devolve o usuario e a organizacao da sessao")
    void meReturnsSessionUser() throws Exception {
        var admin = testData.createActiveAdmin("me");

        var session = (MockHttpSession) mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.email(), admin.password())))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession();

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(admin.organizationId().toString()))
                .andExpect(jsonPath("$.email").value(admin.email()));
    }

    @Test
    @DisplayName("logout encerra a sessao")
    void logoutEndsSession() throws Exception {
        var admin = testData.createActiveAdmin("logout");

        var session = (MockHttpSession) mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.email(), admin.password())))
                .andReturn()
                .getRequest()
                .getSession();

        mockMvc.perform(post("/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}
