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
import com.jhcomp.prospect.shared.security.SecurityConfig;

/**
 * Session authentication (RF-001, RF-002, decision D1).
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Authentication")
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
    @DisplayName("valid credentials authenticate and resolve the organisation")
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
                // The password hash never appears in the response.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("a successful login is recorded in the audit trail")
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
     * RF-001's acceptance criterion requires the refusal not to reveal whether
     * the e-mail exists. Asserting an equal status is not enough: the body has
     * to be indistinguishable too, or the difference allows account
     * enumeration.
     */
    @Nested
    @DisplayName("a login failure does not reveal the cause")
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
        @DisplayName("unknown e-mail, wrong password, disabled user and suspended organisation answer identically")
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

            // And the body must mention no user, e-mail or organisation.
            assertThat(unknownEmail)
                    .contains("INVALID_CREDENTIALS")
                    .doesNotContain("disabled", "suspended", "not found", "revoked");
        }
    }

    @Test
    @DisplayName("a login failure is recorded without storing the e-mail attempted")
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
        // The e-mail attempted would be personal data about someone who may
        // not even be a user, collected with no necessary purpose (doc 07 §2).
        assertThat(failures).allSatisfy(event -> {
            assertThat(event.getDetail()).doesNotContain(attemptedEmail);
            assertThat(event.getActorUserId()).isNull();
        });
    }

    @Test
    @DisplayName("logout is recorded in the audit trail")
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
    @DisplayName("an invalid e-mail fails validation with a per-field error")
    void invalidEmailFailsValidation() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nao-e-email", "senha-qualquer-123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                // MockMvc does not apply the context path, which is why it
                // appears here without the /api/v1 prefix the real server
                // includes.
                .andExpect(jsonPath("$.instance").value("/auth/login"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    @DisplayName("a short password fails validation without reaching the database")
    void shortPasswordFailsValidation() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alguem@example.test", "curta")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    @DisplayName("the CSRF cookie is readable by the page served at the root")
    void csrfCookieIsReadableFromRoot() {
        // It exercises the repository directly rather than through an HTTP
        // request: the cookie is only reissued when the token is new, which
        // would make the assertion depend on test order.
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();

        var token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);

        var cookie = response.getCookie(SecurityConfig.CSRF_COOKIE_NAME);

        assertThat(cookie).describedAs("cookie %s", SecurityConfig.CSRF_COOKIE_NAME).isNotNull();
        // A project-specific name: cookies are not isolated by port, and a
        // generic name would collide with another service on localhost, making
        // the browser send two cookies with the same name.
        assertThat(cookie.getName()).doesNotStartWith("XSRF");
        // Without path "/", the cookie would inherit the /api/v1 context path
        // and the JavaScript of the page served at / would not see it: login
        // would fail with 403 in the browser while passing in an API test.
        assertThat(cookie.getPath()).isEqualTo("/");
        // This cookie has to be readable by JavaScript, unlike the session one.
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    @DisplayName("a login without a CSRF token is refused")
    void loginRequiresCsrfToken() throws Exception {
        var admin = testData.createActiveAdmin("csrf");

        // Without .with(csrf()). With cookie authentication, accepting a POST
        // with no token would allow a request forged from another site.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.email(), admin.password())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("too many attempts result in 429")
    void tooManyAttemptsAreBlocked() throws Exception {
        var admin = testData.createActiveAdmin("rate-limit");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(admin.email(), "senha-errada-123")))
                    .andExpect(status().isUnauthorized());
        }

        // The correct password is refused too, for as long as the block lasts.
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(admin.email(), admin.password())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("/auth/me requires authentication")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("/auth/me returns the session's user and organisation")
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
    @DisplayName("logout ends the session")
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
