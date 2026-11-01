package com.jhcomp.prospect.dashboard;

import static org.hamcrest.Matchers.contains;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** Atencao, funil, responsavel, qualidade dos dados e equipe (RF-070, RF-071). Dados ficticios. */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Dashboard: atencao, funil e responsavel")
class DashboardInsightsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private IdentityTestData.Member admin;
    private MockHttpSession session;

    DashboardInsightsIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = testData.createActiveAdmin("insights");
        session = TestSessions.login(mockMvc, admin);
    }

    private ResultActions send(MockHttpSession as, MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.session(as).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createLead(MockHttpSession as, String name, String segment) throws Exception {
        String segmentJson = segment == null ? "" : ", \"segment\": \"%s\"".formatted(segment);
        String companyId = JsonPath.read(send(as, post("/companies"), """
                {"name": "%s", "city": "Cidade Exemplo", "state": "MG"%s}
                """.formatted(name, segmentJson)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.id");
        return JsonPath.read(send(as, post("/leads"), "{\"companyId\": \"%s\"}".formatted(companyId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void move(String leadId, String... statuses) throws Exception {
        for (String target : statuses) {
            send(session, patch("/leads/" + leadId + "/status"),
                    "{\"status\": \"%s\", \"contactConfirmed\": true}".formatted(target))
                    .andExpect(status().isOk());
        }
    }

    private void backdateStage(String leadId, int days) {
        jdbc.update("update lead_status_change set changed_at = now() - make_interval(days => ?) "
                + "where lead_id = ? and recorded_seq = (select max(recorded_seq) from lead_status_change where lead_id = ?)",
                days, UUID.fromString(leadId), UUID.fromString(leadId));
    }

    private String createTask(String leadId, String title) throws Exception {
        return JsonPath.read(send(session, post("/tasks"), "{\"leadId\": \"%s\", \"title\": \"%s\"}".formatted(leadId, title))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("atencao: tarefa atrasada, lead parado, proposta sem contato e lead sem tarefa; nao contatar fica fora")
    void attention() throws Exception {
        String late = createLead(session, "Loja Tarefa Atrasada", null);
        move(late, "QUALIFIED");
        String task = createTask(late, "Ligar de novo");
        jdbc.update("update task set due_at = now() - interval '2 days' where id = ?", UUID.fromString(task));

        String stale = createLead(session, "Loja Parada", null);
        move(stale, "QUALIFIED");
        backdateStage(stale, 10);
        createTask(stale, "Revisar");

        String proposal = createLead(session, "Loja Proposta Fria", null);
        move(proposal, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");
        backdateStage(proposal, 9);
        createTask(proposal, "Cobrar retorno");

        String blocked = createLead(session, "Loja Bloqueada", null);
        move(blocked, "QUALIFIED");
        backdateStage(blocked, 30);
        move(blocked, "DO_NOT_CONTACT");
        backdateStage(blocked, 30);

        mockMvc.perform(get("/dashboard/attention").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.total").value(1))
                .andExpect(jsonPath("$.tasks.items[0].leadId").value(late))
                .andExpect(jsonPath("$.tasks.items[0].detail").value("Ligar de novo"))
                .andExpect(jsonPath("$.tasks.items[0].overdue").value(true))
                .andExpect(jsonPath("$.staleLeads.items[*].leadId", hasItem(stale)))
                .andExpect(jsonPath("$.staleLeads.items[*].leadId", hasItem(proposal)))
                .andExpect(jsonPath("$.staleLeads.items[*].leadId", not(hasItem(late))))
                .andExpect(jsonPath("$.quietProposals.total").value(1))
                .andExpect(jsonPath("$.quietProposals.items[0].leadId").value(proposal))
                // Todos acima tem tarefa aberta: nenhum aparece como sem tarefa.
                .andExpect(jsonPath("$.withoutTask.total").value(0))
                .andExpect(jsonPath("$..leadId", not(hasItem(blocked))));

        // Interacao recente tira a proposta da lista de frias.
        send(session, post("/leads/" + proposal + "/interactions"), """
                {"type": "CONTACT", "channel": "PHONE", "direction": "OUTBOUND", "occurredAt": "%s"}
                """.formatted(Instant.now())).andExpect(status().isCreated());
        String noTask = createLead(session, "Loja Sem Tarefa", null);
        move(noTask, "QUALIFIED");

        mockMvc.perform(get("/dashboard/attention").param("limit", "1").session(session))
                .andExpect(jsonPath("$.quietProposals.total").value(0))
                .andExpect(jsonPath("$.withoutTask.total").value(1))
                .andExpect(jsonPath("$.withoutTask.items[0].leadId").value(noTask))
                .andExpect(jsonPath("$.staleLeads.total").value(2))
                .andExpect(jsonPath("$.staleLeads.items.length()").value(1));
    }

    @Test
    @DisplayName("funil: alcance por etapa e tempo medio so com amostra suficiente")
    void funnel() throws Exception {
        for (int i = 0; i < 10; i++) {
            String lead = createLead(session, "Loja Funil " + i, null);
            move(lead, "QUALIFIED");
            if (i < 4) {
                move(lead, "CONTACT_APPROVED");
            }
            if (i == 0) {
                move(lead, "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");
                send(session, put("/leads/" + lead + "/deal"), "{\"estimatedValue\": 100}").andExpect(status().isOk());
                move(lead, "WON");
            }
            if (i == 9) {
                move(lead, "LOST");
            }
        }

        mockMvc.perform(get("/dashboard/funnel").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funnel.cohort").value(10))
                .andExpect(jsonPath("$.funnel.minimumSample").value(10))
                .andExpect(jsonPath("$.funnel.stages[*].status",
                        contains("NEW", "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT", "WON")))
                .andExpect(jsonPath("$.funnel.stages[*].reached", contains(10, 10, 4, 1, 1, 1, 1)))
                // 10 sairam de NEW: ha media; de CONTACT_APPROVED so 1 saiu: sem media.
                .andExpect(jsonPath("$.funnel.stages[0].exits").value(10))
                .andExpect(jsonPath("$.funnel.stages[0].sufficientSample").value(true))
                .andExpect(jsonPath("$.funnel.stages[0].avgDays").exists())
                .andExpect(jsonPath("$.funnel.stages[2].sufficientSample").value(false))
                .andExpect(jsonPath("$.funnel.stages[2].avgDays").doesNotExist());

        mockMvc.perform(get("/dashboard/funnel").param("from", "2020-01-01").param("to", "2020-01-31").session(session))
                .andExpect(jsonPath("$.funnel.cohort").value(0));
    }

    @Test
    @DisplayName("responsavel filtra os numeros do dashboard; id de outra organizacao devolve zero")
    void assignee() throws Exception {
        IdentityTestData.Member operator = testData.addMember(admin.organizationId(), OrganizationRole.OPERATOR);
        String mine = createLead(session, "Loja Da Operadora", null);
        createLead(session, "Loja Sem Responsavel", null);
        send(session, patch("/leads/" + mine), "{\"assignedUserId\": \"%s\"}".formatted(operator.userId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/dashboard/summary").param("assignee", operator.userId().toString()).session(session))
                .andExpect(jsonPath("$.summary.leadsByStatus.NEW").value(1))
                .andExpect(jsonPath("$.summary.companiesAdded").value(1));
        mockMvc.perform(get("/dashboard/summary").session(session))
                .andExpect(jsonPath("$.summary.leadsByStatus.NEW").value(2));
        mockMvc.perform(get("/dashboard/summary").param("assignee", UUID.randomUUID().toString()).session(session))
                .andExpect(jsonPath("$.summary.leadsByStatus.NEW").doesNotExist())
                .andExpect(jsonPath("$.summary.companiesAdded").value(0));
        mockMvc.perform(get("/dashboard/summary").param("assignee", "nao-e-uuid").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("qualidade dos dados conta empresas ativas sem segmento")
    void withoutSegment() throws Exception {
        createLead(session, "Loja Sem Segmento", null);
        createLead(session, "Loja Com Segmento", "Barbearia");

        mockMvc.perform(get("/dashboard/summary").session(session))
                .andExpect(jsonPath("$.summary.companiesWithoutSegment").value(1));
    }

    @Test
    @DisplayName("equipe: operador lista os membros ativos so com id e nome")
    void team() throws Exception {
        IdentityTestData.Member operator = testData.addMember(admin.organizationId(), OrganizationRole.OPERATOR);
        MockHttpSession operatorSession = TestSessions.login(mockMvc, operator);

        mockMvc.perform(get("/team").session(operatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].userId", hasItem(admin.userId().toString())))
                .andExpect(jsonPath("$.content[*].userId", hasItem(operator.userId().toString())))
                .andExpect(jsonPath("$.content[*].displayName", hasItem("Usuario de Teste")))
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
    }

    @Test
    @DisplayName("atencao, funil e equipe de A nao mostram dados de B")
    void isolation() throws Exception {
        IdentityTestData.Member other = testData.createActiveAdmin("insights-b");
        MockHttpSession sessionB = TestSessions.login(mockMvc, other);
        String leadB = createLead(sessionB, "Empresa de B", null);
        send(sessionB, patch("/leads/" + leadB + "/status"), "{\"status\": \"QUALIFIED\"}").andExpect(status().isOk());

        mockMvc.perform(get("/dashboard/attention").session(session))
                .andExpect(jsonPath("$..leadId", not(hasItem(leadB))))
                .andExpect(jsonPath("$.withoutTask.total").value(0));
        mockMvc.perform(get("/dashboard/funnel").session(session))
                .andExpect(jsonPath("$.funnel.cohort").value(0));
        mockMvc.perform(get("/team").session(session))
                .andExpect(jsonPath("$.content[*].userId", not(hasItem(other.userId().toString()))));
    }
}
