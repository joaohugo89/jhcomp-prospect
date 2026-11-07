package com.jhcomp.prospect.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** Outreach and do-not-contact keep the organisation scope (doc 10 §3, rule 2). */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Isolation between organisations: outreach and do-not-contact")
class OutreachIsolationTest extends PostgresIntegrationTest {

    private static final String SHARED_PHONE = "(35) 97777-0000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private MockHttpSession sessionA;
    private MockHttpSession sessionB;
    private IdentityTestData.Member memberA;
    private String leadB;
    private String messageB;

    OutreachIsolationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    private String body(MockHttpServletRequestBuilder request, MockHttpSession session, String json) throws Exception {
        return mockMvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    private String createLead(MockHttpSession session, String name) throws Exception {
        String company = JsonPath.read(body(post("/companies"), session, """
                {"name": "%s", "city": "Cidade Exemplo", "state": "MG", "phone": "%s"}
                """.formatted(name, SHARED_PHONE)), "$.id");
        return JsonPath.read(body(post("/leads"), session, "{\"companyId\": \"%s\"}".formatted(company)), "$.id");
    }

    @BeforeEach
    void setUp() throws Exception {
        memberA = testData.createActiveAdmin("outreach-a");
        sessionA = TestSessions.login(mockMvc, memberA);
        sessionB = TestSessions.login(mockMvc, testData.createActiveAdmin("outreach-b"));

        leadB = createLead(sessionB, "Empresa de B");
        messageB = JsonPath.read(body(post("/leads/" + leadB + "/messages/generate"), sessionB,
                "{\"channel\": \"WHATSAPP\"}"), "$.id");
    }

    private void expectNotFound(MockHttpServletRequestBuilder request, String json) throws Exception {
        mockMvc.perform(request.session(sessionA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("a user of A cannot read, generate, edit, approve, record or cancel a message of B")
    void routesAreScoped() throws Exception {
        expectNotFound(get("/leads/" + leadB + "/outreach-context"), "");
        expectNotFound(get("/leads/" + leadB + "/messages"), "");
        expectNotFound(post("/leads/" + leadB + "/messages/generate"), "{\"channel\": \"WHATSAPP\"}");
        expectNotFound(patch("/messages/" + messageB), "{\"body\": \"Alterada por A\"}");
        expectNotFound(post("/messages/" + messageB + "/approve"), "{\"unverifiedClaimsReviewed\": true}");
        expectNotFound(post("/messages/" + messageB + "/record-send"),
                "{\"result\": \"SENT\", \"sentAt\": \"%s\"}".formatted(Instant.now()));
        expectNotFound(post("/messages/" + messageB + "/cancel"), "");

        var message = jdbc.queryForMap("select status, body from outreach_message where id = ?", UUID.fromString(messageB));
        assertThat(message.get("status")).isEqualTo("NEEDS_REVIEW");
        assertThat((String) message.get("body")).doesNotContain("Alterada por A");
        Integer count = jdbc.queryForObject(
                "select count(*) from outreach_message where lead_id = ?", Integer.class, UUID.fromString(leadB));
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("the do-not-contact list is per organisation: a block of B neither blocks A nor appears to A")
    void suppressionIsScoped() throws Exception {
        body(post("/suppression-entries"), sessionB,
                "{\"identifierType\": \"PHONE\", \"identifier\": \"%s\", \"reason\": \"Pedido\"}".formatted(SHARED_PHONE));
        String leadA = createLead(sessionA, "Empresa de A");

        mockMvc.perform(post("/leads/" + leadA + "/messages/generate").session(sessionA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"channel\": \"WHATSAPP\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/suppression-entries").session(sessionA))
                .andExpect(jsonPath("$.totalElements").value(0));
        // Search, totals and the check cannot see B's block either.
        mockMvc.perform(get("/suppression-entries").param("search", SHARED_PHONE).session(sessionA))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/suppression-entries/summary").session(sessionA))
                .andExpect(jsonPath("$.PHONE").value(0));
        mockMvc.perform(post("/suppression-entries/check").session(sessionA).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifierType\": \"PHONE\", \"identifier\": \"%s\"}".formatted(SHARED_PHONE)))
                .andExpect(jsonPath("$.blocked").value(false));
        mockMvc.perform(post("/leads/" + leadB + "/messages/generate").session(sessionB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"channel\": \"WHATSAPP\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("the database refuses a message of A pointing at a lead of B")
    void databaseRejectsForeignLead() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into outreach_message (id, organization_id, lead_id, channel, body, status,
                    created_by_user_id, created_at, updated_at)
                values (?, ?, ?, 'WHATSAPP', 'x', 'DRAFT', ?, now(), now())
                """, UUID.randomUUID(), memberA.organizationId(), UUID.fromString(leadB), memberA.userId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("outreach_message_lead_same_org");
    }
}
