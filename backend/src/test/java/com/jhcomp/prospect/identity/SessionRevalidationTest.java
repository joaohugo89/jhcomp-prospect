package com.jhcomp.prospect.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * Session revalidation on every request.
 *
 * <p>Without it, {@code organizationId} and {@code role} would stay frozen in
 * the session for hours: revoking a membership, disabling a user, suspending
 * an organisation or demoting an administrator would have no effect until the
 * session expired. RF-003's acceptance criterion requires an administrative
 * operation to be refused to whoever lacks permission, and "in eight hours"
 * does not satisfy that criterion.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Session revalidation")
class SessionRevalidationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    @Test
    @DisplayName("a revoked membership ends the session on the next request")
    void revokedMembershipEndsSession() throws Exception {
        var admin = testData.createActiveAdmin("revalid-revoke");
        var session = TestSessions.login(mockMvc, admin);

        // The session works before the revocation.
        mockMvc.perform(get("/auth/me").session(session)).andExpect(status().isOk());

        testData.revokeMembership(admin.memberId());

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("a disabled user ends the session on the next request")
    void disabledUserEndsSession() throws Exception {
        var admin = testData.createActiveAdmin("revalid-disable");
        var session = TestSessions.login(mockMvc, admin);

        testData.disableUser(admin.userId());

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a suspended organisation ends the session on the next request")
    void suspendedOrganizationEndsSession() throws Exception {
        var admin = testData.createActiveAdmin("revalid-suspend");
        var session = TestSessions.login(mockMvc, admin);

        testData.suspendOrganization(admin.organizationId());

        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("demoting an administrator to operator blocks the administrative operation immediately")
    void demotionTakesEffectImmediately() throws Exception {
        var admin = testData.createActiveAdmin("revalid-demote");
        var session = TestSessions.login(mockMvc, admin);

        // While an administrator, they can list members.
        mockMvc.perform(get("/organizations/members").session(session)).andExpect(status().isOk());

        testData.changeRole(admin.memberId(), OrganizationRole.OPERATOR);

        // Demoted: the same session loses access, without having to expire.
        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("FORBIDDEN"));

        // And the role displayed follows the change too.
        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    @DisplayName("promoting an operator to administrator unlocks the operation immediately")
    void promotionTakesEffectImmediately() throws Exception {
        var operator = testData.createActiveOperator("revalid-promote");
        var session = TestSessions.login(mockMvc, operator);

        mockMvc.perform(get("/organizations/members").session(session)).andExpect(status().isForbidden());

        testData.changeRole(operator.memberId(), OrganizationRole.ADMIN);

        mockMvc.perform(get("/organizations/members").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a valid session keeps working across requests")
    void validSessionKeepsWorking() throws Exception {
        var admin = testData.createActiveAdmin("revalid-ok");
        var session = TestSessions.login(mockMvc, admin);

        // Revalidation must not drop a legitimate session.
        for (int request = 0; request < 3; request++) {
            mockMvc.perform(get("/auth/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.organizationId").value(admin.organizationId().toString()));
        }
    }
}
