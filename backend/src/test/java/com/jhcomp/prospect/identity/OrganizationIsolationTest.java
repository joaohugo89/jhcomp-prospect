package com.jhcomp.prospect.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * Isolation between organisations (RNF-001, doc 10 §3).
 *
 * <p>This is the test doc 10 §3 calls critical: two organisations, A and B,
 * and the verification that no path takes a user of A to data of B — not
 * through a listing, not through a known UUID, not through an administrative
 * role.
 *
 * <p>It checks both the HTTP response and the behaviour in the database, as
 * the document requires.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Isolation between organisations")
class OrganizationIsolationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    private OrganizationEntity organizationA;
    private OrganizationEntity organizationB;
    private IdentityTestData.Member adminA;
    private IdentityTestData.Member memberOfB;

    @BeforeEach
    void setUp() {
        organizationA = testData.createOrganization("org-a", com.jhcomp.prospect.identity.domain.OrganizationStatus.ACTIVE);
        organizationB = testData.createOrganization("org-b", com.jhcomp.prospect.identity.domain.OrganizationStatus.ACTIVE);

        adminA = testData.createMember(
                organizationA, OrganizationRole.ADMIN, UserStatus.ACTIVE, MemberStatus.ACTIVE);
        memberOfB = testData.createMember(
                organizationB, OrganizationRole.ADMIN, UserStatus.ACTIVE, MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("a user of A does not see members of B in the listing")
    void listingIsScopedToOwnOrganization() throws Exception {
        var session = TestSessions.login(mockMvc, adminA);

        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(adminA.userId().toString()))
                // B's member must appear nowhere in the response.
                .andExpect(jsonPath("$.content[?(@.email == '" + memberOfB.email() + "')]").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("a user of A cannot reach a member of B even knowing the UUID")
    void cannotReachOtherOrganizationResourceByUuid() throws Exception {
        var session = TestSessions.login(mockMvc, adminA);

        // The UUID is valid and the resource exists: it simply does not belong
        // to the caller's organisation. The answer is 404, not 403, so as not
        // to confirm it exists.
        mockMvc.perform(get("/organizations/members/" + memberOfB.memberId()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Member not found"));
    }

    @Test
    @DisplayName("a user of A reaches their own member by UUID")
    void canReachOwnOrganizationResourceByUuid() throws Exception {
        var session = TestSessions.login(mockMvc, adminA);

        mockMvc.perform(get("/organizations/members/" + adminA.memberId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(adminA.memberId().toString()));
    }

    @Test
    @DisplayName("a scoped query in the database returns no resource from another organisation")
    void repositoryQueryIsScoped() {
        // The same effect verified one layer below HTTP, as doc 10 §3 asks.
        assertThat(memberRepository.findByIdAndOrganization(memberOfB.memberId(), organizationA.getId()))
                .describedAs("membro de B buscado com escopo de A")
                .isEmpty();

        assertThat(memberRepository.findByIdAndOrganization(memberOfB.memberId(), organizationB.getId()))
                .describedAs("membro de B buscado com escopo de B")
                .isPresent();
    }

    @Test
    @DisplayName("a listing in the database does not mix organisations")
    void repositoryListingIsScoped() {
        var membersOfA = memberRepository.findByOrganization(
                organizationA.getId(), MemberStatus.ACTIVE, PageRequest.of(0, 50));

        assertThat(membersOfA.getContent())
                .allSatisfy(member ->
                        assertThat(member.getOrganization().getId()).isEqualTo(organizationA.getId()));
        assertThat(membersOfA.getContent())
                .noneSatisfy(member -> assertThat(member.getId()).isEqualTo(memberOfB.memberId()));
    }

    @Test
    @DisplayName("an operator cannot perform an administrative operation")
    void operatorIsBlockedFromAdminOperation() throws Exception {
        var operator = testData.createMember(
                organizationA, OrganizationRole.OPERATOR, UserStatus.ACTIVE, MemberStatus.ACTIVE);

        var session = TestSessions.login(mockMvc, operator);

        // RF-003's acceptance criterion.
        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("without authentication there is no access to organisation data")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/organizations/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/organizations/members/" + memberOfB.memberId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an organizationId sent by the client is ignored")
    void clientSuppliedOrganizationIdIsIgnored() throws Exception {
        var session = TestSessions.login(mockMvc, adminA);

        // An explicit attempt to switch organisation through a query string.
        // The parameter is read nowhere; the scope comes from the session
        // (RF-002).
        mockMvc.perform(get("/organizations/members")
                        .param("organizationId", organizationB.getId().toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(adminA.userId().toString()));
    }

    @Test
    @DisplayName("a session of B cannot see data of A")
    void reverseDirectionIsAlsoIsolated() throws Exception {
        // Tests the opposite direction: isolation that works one way only
        // usually means the filter was applied in the wrong place.
        var session = TestSessions.login(mockMvc, memberOfB);

        mockMvc.perform(get("/organizations/members/" + adminA.memberId()).session(session))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(memberOfB.userId().toString()));
    }
}
