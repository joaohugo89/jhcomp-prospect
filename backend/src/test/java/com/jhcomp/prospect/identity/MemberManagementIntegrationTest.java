package com.jhcomp.prospect.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** Member management and password change (RF-003, D28). */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Member management and passwords")
class MemberManagementIntegrationTest extends PostgresIntegrationTest {

    private static final String FINAL_PASSWORD = "senha-definitiva-ficticia";

    /**
     * Addresses in 198.19.0.0/16 (RFC 2544), one per test: the password change
     * uses the login limiter, which is shared across the whole context.
     */
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private IdentityTestData.Member admin;
    private MockHttpSession adminSession;
    private String address;

    MemberManagementIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = testData.createActiveAdmin("members");
        adminSession = TestSessions.login(mockMvc, admin);
        int index = NEXT_ADDRESS.incrementAndGet();
        address = "198.19." + (index / 250) + "." + (index % 250 + 1);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, MockHttpSession session, String json)
            throws Exception {
        return mockMvc.perform(request.session(session).with(csrf()).with(r -> {
            r.setRemoteAddr(address);
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Registers a member through the API and returns the login data with the temporary password. */
    private IdentityTestData.Member createMember(String role) throws Exception {
        String email = "membro-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        String body = perform(post("/organizations/members"), adminSession, """
                {"email": "%s", "displayName": "Membro Ficticio", "role": "%s"}
                """.formatted(email, role))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new IdentityTestData.Member(
                UUID.fromString(JsonPath.read(body, "$.member.memberId")),
                UUID.fromString(JsonPath.read(body, "$.member.userId")),
                admin.organizationId(),
                email,
                JsonPath.read(body, "$.temporaryPassword"));
    }

    private ResultActions changePassword(MockHttpSession session, String current, String next) throws Exception {
        return perform(post("/auth/password"), session,
                "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, next));
    }

    /** A member who has already changed the temporary password; returns their session. */
    private MockHttpSession activeSession(IdentityTestData.Member member) throws Exception {
        MockHttpSession session = TestSessions.login(mockMvc, member);
        changePassword(session, member.password(), FINAL_PASSWORD).andExpect(status().isOk());
        return session;
    }

    private IdentityTestData.Member withPassword(IdentityTestData.Member member, String password) {
        return new IdentityTestData.Member(
                member.memberId(), member.userId(), member.organizationId(), member.email(), password);
    }

    // ---- Team overview ------------------------------------------------------

    @Test
    @DisplayName("the list shows last access and a pending password, searches by name or e-mail, and filters by status")
    void teamOverview() throws Exception {
        IdentityTestData.Member waiting = createMember("OPERATOR");
        IdentityTestData.Member active = createMember("OPERATOR");
        activeSession(active);
        perform(patch("/organizations/members/" + waiting.memberId()), adminSession, "{\"status\": \"REVOKED\"}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/organizations/members").param("search", active.email().toUpperCase())
                        .param("includeRevoked", "true").session(adminSession))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].lastLoginAt").exists())
                .andExpect(jsonPath("$.content[0].passwordChangeRequired").value(false));
        mockMvc.perform(get("/organizations/members").param("status", "REVOKED").session(adminSession))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].memberId").value(waiting.memberId().toString()))
                .andExpect(jsonPath("$.content[0].passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.content[0].lastLoginAt").doesNotExist());
        // A LIKE wildcard is literal: "%" does not return everyone.
        mockMvc.perform(get("/organizations/members").param("search", "%").param("includeRevoked", "true")
                        .session(adminSession))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/organizations/members/summary").session(adminSession))
                .andExpect(jsonPath("$.active").value(2))
                .andExpect(jsonPath("$.admins").value(1))
                .andExpect(jsonPath("$.revoked").value(1))
                .andExpect(jsonPath("$.awaitingFirstAccess").value(0));

        // Another organisation does not enter the count, and an operator cannot read it.
        IdentityTestData.Member other = testData.createActiveAdmin("membros-resumo-b");
        mockMvc.perform(get("/organizations/members/summary").session(TestSessions.login(mockMvc, other)))
                .andExpect(jsonPath("$.active").value(1))
                .andExpect(jsonPath("$.revoked").value(0));
        mockMvc.perform(get("/organizations/members/summary").session(activeSession(createMember("OPERATOR"))))
                .andExpect(status().isForbidden());
    }

    // ---- Registration and first access --------------------------------------

    @Test
    @DisplayName("registration generates a temporary password that appears only in the response and is never stored in clear text")
    void createsMemberWithTemporaryPassword() throws Exception {
        IdentityTestData.Member member = createMember("OPERATOR");

        assertThat(member.password()).hasSize(20);
        var row = jdbc.queryForMap(
                "select password_hash, password_change_required from app_user where id = ?", member.userId());
        assertThat((String) row.get("password_hash")).doesNotContain(member.password()).startsWith("{bcrypt}");
        assertThat(row.get("password_change_required")).isEqualTo(true);

        var audit = jdbc.queryForMap("select action, detail from audit_event where resource_id = ?",
                member.memberId().toString());
        assertThat(audit).containsEntry("action", "MEMBER_CREATE").containsEntry("detail", "OPERATOR");
        assertThat(String.valueOf(audit.get("detail"))).doesNotContain(member.password());
    }

    @Test
    @DisplayName("with a temporary password only the password change is available; after it, normal access")
    void temporaryPasswordMustBeChanged() throws Exception {
        IdentityTestData.Member member = createMember("OPERATOR");
        MockHttpSession session = TestSessions.login(mockMvc, member);

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        mockMvc.perform(get("/leads").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("PASSWORD_CHANGE_REQUIRED"));

        changePassword(session, "senha-errada-ficticia", FINAL_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("INVALID_CREDENTIALS"));
        changePassword(session, member.password(), "curta").andExpect(status().isBadRequest());
        changePassword(session, member.password(), member.password()).andExpect(status().isBadRequest());

        changePassword(session, member.password(), FINAL_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
        mockMvc.perform(get("/leads").session(session)).andExpect(status().isOk());

        // The temporary password stops working; the new one gets in directly, with no requirement.
        mockMvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(member.email(), member.password())))
                .andExpect(status().isUnauthorized());
        MockHttpSession again = TestSessions.login(mockMvc, withPassword(member, FINAL_PASSWORD));
        mockMvc.perform(get("/auth/me").session(again)).andExpect(jsonPath("$.passwordChangeRequired").value(false));
    }

    @Test
    @DisplayName("changing the password ends the user's other sessions and keeps the current one")
    void passwordChangeEndsOtherSessions() throws Exception {
        IdentityTestData.Member created = createMember("OPERATOR");
        activeSession(created);
        IdentityTestData.Member member = withPassword(created, FINAL_PASSWORD);
        MockHttpSession first = TestSessions.login(mockMvc, member);
        MockHttpSession second = TestSessions.login(mockMvc, member);

        changePassword(first, FINAL_PASSWORD, "outra-senha-ficticia-2").andExpect(status().isOk());

        mockMvc.perform(get("/auth/me").session(second)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me").session(first)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("wrong attempts at the current password run into the login limiter")
    void passwordChangeIsRateLimited() throws Exception {
        IdentityTestData.Member member = createMember("OPERATOR");
        MockHttpSession session = TestSessions.login(mockMvc, member);

        for (int attempt = 0; attempt < 5; attempt++) {
            changePassword(session, "senha-errada-ficticia", FINAL_PASSWORD).andExpect(status().isBadRequest());
        }
        changePassword(session, member.password(), FINAL_PASSWORD).andExpect(status().isTooManyRequests());
    }

    // ---- Role, access and reset ---------------------------------------------

    @Test
    @DisplayName("promoting, revoking and restoring take effect on the member's next request")
    void roleAndStatusChangesApplyImmediately() throws Exception {
        IdentityTestData.Member member = createMember("OPERATOR");
        MockHttpSession session = activeSession(member);
        mockMvc.perform(get("/organizations/members").session(session)).andExpect(status().isForbidden());

        perform(patch("/organizations/members/" + member.memberId()), adminSession, "{\"role\": \"ADMIN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(get("/organizations/members").session(session)).andExpect(status().isOk());

        perform(patch("/organizations/members/" + member.memberId()), adminSession, "{\"status\": \"REVOKED\"}")
                .andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/organizations/members?includeRevoked=true").session(adminSession))
                .andExpect(jsonPath("$.content[?(@.status == 'REVOKED')].memberId").value(member.memberId().toString()));

        perform(patch("/organizations/members/" + member.memberId()), adminSession, "{\"status\": \"ACTIVE\"}")
                .andExpect(status().isOk());
        TestSessions.login(mockMvc, withPassword(member, FINAL_PASSWORD));
    }

    @Test
    @DisplayName("resetting the password ends the member's session and requires a new change")
    void resetPassword() throws Exception {
        IdentityTestData.Member member = createMember("OPERATOR");
        MockHttpSession session = activeSession(member);

        String body = perform(post("/organizations/members/" + member.memberId() + "/reset-password"), adminSession, "")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String temporary = JsonPath.read(body, "$.temporaryPassword");

        mockMvc.perform(get("/auth/me").session(session)).andExpect(status().isUnauthorized());
        MockHttpSession again = TestSessions.login(mockMvc, withPassword(member, temporary));
        mockMvc.perform(get("/auth/me").session(again)).andExpect(jsonPath("$.passwordChangeRequired").value(true));
    }

    @Test
    @DisplayName("an administrator cannot change their own membership")
    void cannotChangeSelf() throws Exception {
        perform(patch("/organizations/members/" + admin.memberId()), adminSession, "{\"role\": \"OPERATOR\"}")
                .andExpect(status().isConflict());
        perform(patch("/organizations/members/" + admin.memberId()), adminSession, "{\"status\": \"REVOKED\"}")
                .andExpect(status().isConflict());
        perform(post("/organizations/members/" + admin.memberId() + "/reset-password"), adminSession, "")
                .andExpect(status().isConflict());

        var row = jdbc.queryForMap("select role, status from organization_member where id = ?", admin.memberId());
        assertThat(row).containsEntry("role", "ADMIN").containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("an already registered e-mail is refused, even from another organisation")
    void duplicateEmailIsRejected() throws Exception {
        IdentityTestData.Member other = testData.createActiveAdmin("members-other");

        perform(post("/organizations/members"), adminSession, """
                {"email": "%s", "displayName": "Duplicado", "role": "OPERATOR"}
                """.formatted(other.email().toUpperCase()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an operator cannot register, change or reset a member's password")
    void operatorCannotManageMembers() throws Exception {
        IdentityTestData.Member operator = createMember("OPERATOR");
        MockHttpSession session = activeSession(operator);

        perform(post("/organizations/members"), session,
                "{\"email\": \"x@example.test\", \"displayName\": \"X\", \"role\": \"ADMIN\"}")
                .andExpect(status().isForbidden());
        perform(patch("/organizations/members/" + admin.memberId()), session, "{\"status\": \"REVOKED\"}")
                .andExpect(status().isForbidden());
        perform(post("/organizations/members/" + admin.memberId() + "/reset-password"), session, "")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an administrator of A cannot change or reset a member of B")
    void crossOrganizationIsNotFound() throws Exception {
        IdentityTestData.Member other = testData.createActiveOperator("members-b");

        perform(patch("/organizations/members/" + other.memberId()), adminSession, "{\"status\": \"REVOKED\"}")
                .andExpect(status().isNotFound());
        perform(post("/organizations/members/" + other.memberId() + "/reset-password"), adminSession, "")
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select status from organization_member where id = ?", String.class,
                other.memberId())).isEqualTo("ACTIVE");
        TestSessions.login(mockMvc, other);
    }
}
