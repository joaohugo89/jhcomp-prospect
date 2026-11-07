package com.jhcomp.prospect.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * Isolation between organisations across companies and leads (doc 10 §3).
 *
 * <p>A user of A knows B's UUIDs and tries every route with them. Each
 * attempt answers 404 rather than 403, so as not to confirm the resource
 * exists; and the database confirms that nothing of B's changed.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Isolation between organisations: companies and leads")
class ProspectingIsolationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private IdentityTestData.Member memberB;
    private MockHttpSession sessionA;
    private MockHttpSession sessionB;
    private String companyB;
    private String leadB;

    ProspectingIsolationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        sessionA = TestSessions.login(mockMvc, testData.createActiveAdmin("iso-a"));
        memberB = testData.createActiveAdmin("iso-b");
        sessionB = TestSessions.login(mockMvc, memberB);

        companyB = JsonPath.read(send(post("/companies"), sessionB, """
                {"name": "Empresa de B", "city": "Cidade Exemplo", "state": "MG",
                 "websiteUrl": "empresa-b.example.test", "phone": "35 3333-4444"}
                """), "$.id");
        leadB = JsonPath.read(send(post("/leads"), sessionB, """
                {"companyId": "%s"}
                """.formatted(companyB)), "$.id");
    }

    private String send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        return mockMvc.perform(request
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void expectNotFound(MockHttpServletRequestBuilder request, String body) throws Exception {
        mockMvc.perform(request
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("a user of A lists neither companies nor leads of B")
    void listingsAreScoped() throws Exception {
        mockMvc.perform(get("/companies").param("includeArchived", "true").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(companyB))));
        mockMvc.perform(get("/companies").param("search", "Empresa de B").session(sessionA))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/leads").session(sessionA))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(leadB))));
    }

    @Test
    @DisplayName("a user of A cannot read a company of B by UUID, on any route")
    void companyReadsAreScoped() throws Exception {
        expectNotFound(get("/companies/" + companyB), "");
        expectNotFound(get("/companies/" + companyB + "/sources"), "");
        expectNotFound(get("/companies/" + companyB + "/duplicates"), "");
    }

    @Test
    @DisplayName("a user of A can neither change nor archive a company of B")
    void companyWritesAreScoped() throws Exception {
        expectNotFound(patch("/companies/" + companyB), "{\"name\": \"Alterado por A\"}");
        expectNotFound(post("/companies/" + companyB + "/archive"), "");

        var row = jdbc.queryForMap("select name, archived_at from company where id = ?", UUID.fromString(companyB));
        assertThat(row.get("name")).isEqualTo("Empresa de B");
        assertThat(row.get("archived_at")).isNull();
    }

    @Test
    @DisplayName("a user of A cannot open a lead for a company of B")
    void cannotCreateLeadForOtherOrganizationCompany() throws Exception {
        // Closes B's lead so that only the isolation can prevent the creation.
        send(patch("/leads/" + leadB + "/status"), sessionB, "{\"status\": \"LOST\"}");

        expectNotFound(post("/leads"), "{\"companyId\": \"%s\"}".formatted(companyB));

        Integer leads = jdbc.queryForObject(
                "select count(*) from lead where company_id = ?", Integer.class, UUID.fromString(companyB));
        assertThat(leads).isEqualTo(1);
    }

    @Test
    @DisplayName("a user of A can neither read nor change a lead of B by UUID")
    void leadRoutesAreScoped() throws Exception {
        expectNotFound(get("/leads/" + leadB), "");
        expectNotFound(get("/leads/" + leadB + "/timeline"), "");
        expectNotFound(patch("/leads/" + leadB + "/status"), "{\"status\": \"QUALIFIED\"}");
        expectNotFound(patch("/leads/" + leadB + "/priority"), "{\"priority\": \"HIGH\"}");
        expectNotFound(patch("/leads/" + leadB), "{\"assignedUserId\": null}");

        var row = jdbc.queryForMap("select status, priority from lead where id = ?", UUID.fromString(leadB));
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("priority")).isEqualTo("MEDIUM");
        Integer changes = jdbc.queryForObject(
                "select count(*) from lead_status_change where lead_id = ?", Integer.class, UUID.fromString(leadB));
        assertThat(changes).isEqualTo(1);
    }

    @Test
    @DisplayName("a user of A cannot assign their own lead to a member of B")
    void cannotAssignToOtherOrganizationMember() throws Exception {
        String companyA = JsonPath.read(send(post("/companies"), sessionA, """
                {"name": "Empresa de A", "city": "Cidade Exemplo", "state": "MG"}
                """), "$.id");
        String leadA = JsonPath.read(send(post("/leads"), sessionA, """
                {"companyId": "%s"}
                """.formatted(companyA)), "$.id");

        expectNotFound(patch("/leads/" + leadA), "{\"assignedUserId\": \"%s\"}".formatted(memberB.userId()));
    }

    @Test
    @DisplayName("duplicates never suggest a company from another organisation")
    void duplicatesAreScoped() throws Exception {
        // The same domain and phone as B's company: it would be a strong
        // duplicate if it were in the same organisation.
        String companyA = JsonPath.read(send(post("/companies"), sessionA, """
                {"name": "Empresa de B", "city": "Cidade Exemplo", "state": "MG",
                 "websiteUrl": "https://empresa-b.example.test", "phone": "(35) 3333-4444"}
                """), "$.id");

        mockMvc.perform(get("/companies/" + companyA + "/duplicates").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
