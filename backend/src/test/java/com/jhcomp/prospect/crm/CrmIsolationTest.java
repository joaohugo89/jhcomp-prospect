package com.jhcomp.prospect.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** CRM e dashboard mantem o escopo da organizacao (doc 10 §3). */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Isolamento entre organizacoes: CRM e dashboard")
class CrmIsolationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private MockHttpSession sessionA;
    private IdentityTestData.Member memberB;
    private String leadB;
    private String taskB;

    CrmIsolationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    private String body(MockHttpServletRequestBuilder request, MockHttpSession session, String json) throws Exception {
        return mockMvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    @BeforeEach
    void setUp() throws Exception {
        sessionA = TestSessions.login(mockMvc, testData.createActiveAdmin("crm-a"));
        memberB = testData.createActiveAdmin("crm-b");
        MockHttpSession sessionB = TestSessions.login(mockMvc, memberB);

        String companyB = JsonPath.read(body(post("/companies"), sessionB,
                "{\"name\": \"Empresa de B\", \"city\": \"Cidade Exemplo\", \"state\": \"MG\"}"), "$.id");
        leadB = JsonPath.read(body(post("/leads"), sessionB, "{\"companyId\": \"%s\"}".formatted(companyB)), "$.id");
        for (String target : new String[] {"QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT"}) {
            body(patch("/leads/" + leadB + "/status"), sessionB,
                    "{\"status\": \"%s\", \"contactConfirmed\": true}".formatted(target));
        }
        body(post("/leads/" + leadB + "/interactions"), sessionB, """
                {"type": "CONTACT", "channel": "PHONE", "direction": "OUTBOUND", "occurredAt": "%s"}
                """.formatted(Instant.now()));
        taskB = JsonPath.read(body(post("/tasks"), sessionB,
                "{\"leadId\": \"%s\", \"title\": \"Tarefa de B\"}".formatted(leadB)), "$.id");
    }

    private void expectNotFound(MockHttpServletRequestBuilder request, String json) throws Exception {
        mockMvc.perform(request.session(sessionA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("usuario de A nao le nem altera deal, interacoes e tarefas de B")
    void routesAreScoped() throws Exception {
        expectNotFound(get("/leads/" + leadB + "/deal"), "");
        expectNotFound(put("/leads/" + leadB + "/deal"), "{\"estimatedValue\": 1}");
        expectNotFound(get("/leads/" + leadB + "/interactions"), "");
        expectNotFound(post("/leads/" + leadB + "/interactions"), """
                {"type": "NOTE", "channel": "OTHER", "direction": "INTERNAL", "occurredAt": "%s"}
                """.formatted(Instant.now()));
        expectNotFound(get("/leads/" + leadB + "/tasks"), "");
        expectNotFound(post("/tasks"), "{\"leadId\": \"%s\", \"title\": \"Intrusa\"}".formatted(leadB));
        expectNotFound(patch("/tasks/" + taskB), "{\"title\": \"Alterada por A\", \"status\": \"DONE\"}");

        var task = jdbc.queryForMap("select title, status from task where id = ?", UUID.fromString(taskB));
        assertThat(task.get("title")).isEqualTo("Tarefa de B");
        assertThat(task.get("status")).isEqualTo("OPEN");
        Integer interactions = jdbc.queryForObject(
                "select count(*) from interaction where lead_id = ?", Integer.class, UUID.fromString(leadB));
        assertThat(interactions).isEqualTo(1);
    }

    @Test
    @DisplayName("usuario de A nao atribui tarefa propria a membro de B")
    void cannotAssignToOtherOrganization() throws Exception {
        String companyA = JsonPath.read(body(post("/companies"), sessionA,
                "{\"name\": \"Empresa de A\", \"city\": \"Cidade Exemplo\", \"state\": \"MG\"}"), "$.id");
        String leadA = JsonPath.read(body(post("/leads"), sessionA, "{\"companyId\": \"%s\"}".formatted(companyA)), "$.id");

        expectNotFound(post("/tasks"), "{\"leadId\": \"%s\", \"title\": \"Tarefa\", \"assignedUserId\": \"%s\"}"
                .formatted(leadA, memberB.userId()));
    }

    @Test
    @DisplayName("quadro e dashboard de A nao mostram nem contam dados de B")
    void aggregatesAreScoped() throws Exception {
        mockMvc.perform(get("/pipeline/board").session(sessionA))
                .andExpect(jsonPath("$[5].total").value(0))
                .andExpect(jsonPath("$[5].cards[*].leadId", not(hasItem(leadB))));
        mockMvc.perform(get("/dashboard/summary").session(sessionA))
                .andExpect(jsonPath("$.summary.activeLeads").value(0))
                .andExpect(jsonPath("$.summary.contactsRegistered").value(0))
                .andExpect(jsonPath("$.summary.openTasks").value(0));
        mockMvc.perform(get("/dashboard/activity").session(sessionA))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
