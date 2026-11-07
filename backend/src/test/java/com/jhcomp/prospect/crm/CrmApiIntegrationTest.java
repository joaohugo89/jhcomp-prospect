package com.jhcomp.prospect.crm;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** CRM and dashboard through the API (RF-040 to RF-043, RF-070, D2, D25). Fictitious data. */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("CRM and dashboard API")
class CrmApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private MockHttpSession session;

    CrmApiIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        session = TestSessions.login(mockMvc, testData.createActiveOperator("crm"));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createLead(String name) throws Exception {
        String companyId = JsonPath.read(send(post("/companies"), """
                {"name": "%s", "segment": "RETAIL", "city": "Cidade Exemplo", "state": "MG"}
                """.formatted(name)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return JsonPath.read(send(post("/leads"), "{\"companyId\": \"%s\"}".formatted(companyId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void move(String leadId, String... statuses) throws Exception {
        for (String target : statuses) {
            send(patch("/leads/" + leadId + "/status"),
                    "{\"status\": \"%s\", \"contactConfirmed\": true, \"reason\": \"Sem orcamento\"}".formatted(target))
                    .andExpect(status().isOk());
        }
    }

    private String interaction(String type, String direction, Instant occurredAt) {
        return """
                {"type": "%s", "channel": "WHATSAPP", "direction": "%s", "occurredAt": "%s",
                 "outcome": "Retorno combinado", "notes": "Conversa ficticia"}
                """.formatted(type, direction, occurredAt);
    }

    @Test
    @DisplayName("the stages are the lead statuses, in order, without the contact block")
    void stages() throws Exception {
        mockMvc.perform(get("/pipeline-stages").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].key", contains("NEW", "QUALIFIED", "CONTACT_APPROVED", "CONTACTED",
                        "IN_CONVERSATION", "PROPOSAL_SENT", "WON", "LOST")))
                .andExpect(jsonPath("$[6].terminal").value(true));
    }

    @Test
    @DisplayName("the board carries the cards per stage with last interaction and next task")
    void board() throws Exception {
        String first = createLead("Loja Um");
        String second = createLead("Loja Dois");
        move(second, "QUALIFIED");
        send(post("/leads/" + second + "/interactions"), interaction("NOTE", "INTERNAL", Instant.now()))
                .andExpect(status().isCreated());
        send(post("/tasks"), "{\"leadId\": \"%s\", \"title\": \"Ligar de novo\", \"dueAt\": \"%s\"}"
                .formatted(second, Instant.now().plus(2, ChronoUnit.DAYS))).andExpect(status().isCreated());

        mockMvc.perform(get("/pipeline/board").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].stage.key").value("NEW"))
                .andExpect(jsonPath("$[0].total").value(1))
                .andExpect(jsonPath("$[0].cards[0].leadId").value(first))
                .andExpect(jsonPath("$[1].cards[0].companyName").value("Loja Dois"))
                .andExpect(jsonPath("$[1].cards[0].lastInteractionType").value("NOTE"))
                .andExpect(jsonPath("$[1].cards[0].nextTaskTitle").value("Ligar de novo"))
                .andExpect(jsonPath("$[1].cards[0].allowedTransitions", hasItem("CONTACT_APPROVED")));
    }

    /** Board items 2, 3 and 4: time in stage, the whole stage's value, and the final columns' window. */
    @Test
    @DisplayName("the board sums the stage value, reports the stage entry and limits the closed ones to 30 days")
    void boardValuesAndWindows() throws Exception {
        String big = createLead("Loja Valor Alto");
        String small = createLead("Loja Valor Baixo");
        for (String lead : new String[] {big, small}) {
            move(lead, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");
        }
        send(put("/leads/" + big + "/deal"), "{\"estimatedValue\": 1500.50}").andExpect(status().isOk());
        send(put("/leads/" + small + "/deal"), "{\"estimatedValue\": 499.50}").andExpect(status().isOk());
        // It reached the proposal 12 days ago.
        jdbc.update("update lead_status_change set changed_at = now() - interval '12 days' "
                + "where lead_id = ? and to_status = 'PROPOSAL_SENT'", UUID.fromString(big));

        String recent = createLead("Loja Ganha Agora");
        String old = createLead("Loja Ganha Antes");
        for (String lead : new String[] {recent, old}) {
            move(lead, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");
            send(put("/leads/" + lead + "/deal"), "{\"estimatedValue\": 1000}").andExpect(status().isOk());
            move(lead, "WON");
        }
        jdbc.update("update lead set closed_at = now() - interval '40 days' where id = ?", UUID.fromString(old));

        String board = mockMvc.perform(get("/pipeline/board").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[5].stage.key").value("PROPOSAL_SENT"))
                .andExpect(jsonPath("$[5].total").value(2))
                .andExpect(jsonPath("$[5].totalValue").value(2000.00))
                .andExpect(jsonPath("$[5].recentDays").doesNotExist())
                .andExpect(jsonPath("$[5].cards[*].estimatedValue", hasItem(1500.50)))
                // Closed 40 days ago leaves the column and the value; it stays in the lead list.
                .andExpect(jsonPath("$[6].stage.key").value("WON"))
                .andExpect(jsonPath("$[6].recentDays").value(30))
                .andExpect(jsonPath("$[6].total").value(1))
                .andExpect(jsonPath("$[6].totalValue").value(1000.00))
                .andExpect(jsonPath("$[6].cards[*].leadId", contains(recent)))
                .andReturn().getResponse().getContentAsString();

        List<String> entered = JsonPath.read(board, "$[5].cards[?(@.leadId == '" + big + "')].stageEnteredAt");
        assertThat(Instant.parse(entered.getFirst())).isBefore(Instant.now().minus(11, ChronoUnit.DAYS));

        // The value respects the board's filters, as the count does.
        mockMvc.perform(get("/pipeline/board").param("segment", "OUTRO").session(session))
                .andExpect(jsonPath("$[5].total").value(0))
                .andExpect(jsonPath("$[5].totalValue").value(0));
        mockMvc.perform(get("/leads").param("status", "WON").session(session))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    /** D2: the deal appears at the proposal and the outcome comes from the lead's status. */
    @Test
    @DisplayName("the deal appears at the proposal, stores a decimal value and a loss reason")
    void dealLifecycle() throws Exception {
        String leadId = createLead("Loja Deal");
        mockMvc.perform(get("/leads/" + leadId + "/deal").session(session)).andExpect(status().isNotFound());
        send(put("/leads/" + leadId + "/deal"), "{\"service\": \"Site\"}").andExpect(status().isConflict());

        move(leadId, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");

        send(put("/leads/" + leadId + "/deal"), "{\"service\": \"Site institucional\", \"estimatedValue\": 1500.50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedValue").value(1500.50))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.outcome").value("OPEN"));

        move(leadId, "LOST");
        mockMvc.perform(get("/leads/" + leadId + "/deal").session(session))
                .andExpect(jsonPath("$.outcome").value("LOST"))
                .andExpect(jsonPath("$.lossReason").value("Sem orcamento"))
                .andExpect(jsonPath("$.service").value("Site institucional"));
    }

    @Test
    @DisplayName("a win without passing through a proposal also creates the deal")
    void wonWithoutProposal() throws Exception {
        String leadId = createLead("Loja Ganha");
        move(leadId, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "WON");

        mockMvc.perform(get("/leads/" + leadId + "/deal").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("WON"));
    }

    @Test
    @DisplayName("a negative value, or one with more than two decimal places, is refused")
    void dealValueValidation() throws Exception {
        String leadId = createLead("Loja Valor");
        move(leadId, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");

        send(put("/leads/" + leadId + "/deal"), "{\"estimatedValue\": -1}").andExpect(status().isBadRequest());
        send(put("/leads/" + leadId + "/deal"), "{\"estimatedValue\": 10.123}").andExpect(status().isBadRequest());
    }

    /** Rule 6: recording a contact does not move the lead to CONTACTED. */
    @Test
    @DisplayName("a recorded interaction does not change the status and appears in the history")
    void interactionDoesNotMoveLead() throws Exception {
        String leadId = createLead("Loja Contato");
        move(leadId, "QUALIFIED", "CONTACT_APPROVED");

        send(post("/leads/" + leadId + "/interactions"), interaction("CONTACT", "OUTBOUND", Instant.now()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CONTACT"));

        mockMvc.perform(get("/leads/" + leadId).session(session)).andExpect(jsonPath("$.status").value("CONTACT_APPROVED"));
        mockMvc.perform(get("/leads/" + leadId + "/timeline").session(session))
                .andExpect(jsonPath("$.content[0].type").value("INTERACTION"))
                .andExpect(jsonPath("$.content[0].interactionType").value("CONTACT"))
                .andExpect(jsonPath("$.content[0].channel").value("WHATSAPP"));
        mockMvc.perform(get("/leads/" + leadId + "/interactions").session(session))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("an interaction in the future is refused")
    void futureInteraction() throws Exception {
        String leadId = createLead("Loja Futuro");

        send(post("/leads/" + leadId + "/interactions"), interaction("NOTE", "INTERNAL", Instant.now().plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isBadRequest());
    }

    /** Doc 06 §9 and rule 4: a do-not-contact request takes effect at once and blocks any approach. */
    @Test
    @DisplayName("a do-not-contact request blocks the lead and refuses a new approach")
    void doNotContactRequest() throws Exception {
        String leadId = createLead("Loja Bloqueada");
        move(leadId, "QUALIFIED");

        send(post("/leads/" + leadId + "/interactions"), interaction("DO_NOT_CONTACT_REQUEST", "INBOUND", Instant.now()))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/leads/" + leadId).session(session)).andExpect(jsonPath("$.status").value("DO_NOT_CONTACT"));

        send(post("/leads/" + leadId + "/interactions"), interaction("CONTACT", "OUTBOUND", Instant.now()))
                .andExpect(status().isConflict());
        // The company can still come to us: recording what it did remains possible.
        send(post("/leads/" + leadId + "/interactions"), interaction("REPLY", "INBOUND", Instant.now()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("task: creates, completes, and lists the open ones first")
    void tasks() throws Exception {
        String leadId = createLead("Loja Tarefa");
        String done = JsonPath.read(send(post("/tasks"), "{\"leadId\": \"%s\", \"title\": \"Enviar orcamento\"}".formatted(leadId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        send(post("/tasks"), "{\"leadId\": \"%s\", \"title\": \"Agendar visita\"}".formatted(leadId))
                .andExpect(status().isCreated());

        send(patch("/tasks/" + done), "{\"title\": \"Enviar orcamento\", \"status\": \"DONE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(get("/leads/" + leadId + "/tasks").session(session))
                .andExpect(jsonPath("$.content[*].title", contains("Agendar visita", "Enviar orcamento")));
    }

    @Test
    @DisplayName("the dashboard counts leads per stage, contacts and wins for the period")
    void dashboard() throws Exception {
        String won = createLead("Loja Painel Ganha");
        String contacted = createLead("Loja Painel Contato");
        move(won, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");
        send(put("/leads/" + won + "/deal"), "{\"estimatedValue\": 2000}").andExpect(status().isOk());
        move(won, "WON");
        send(post("/leads/" + contacted + "/interactions"), interaction("CONTACT", "OUTBOUND", Instant.now()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/dashboard/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.leadsByStatus.WON").value(1))
                .andExpect(jsonPath("$.summary.leadsByStatus.NEW").value(1))
                .andExpect(jsonPath("$.summary.activeLeads").value(1))
                .andExpect(jsonPath("$.summary.companiesAdded").value(2))
                .andExpect(jsonPath("$.summary.contactsRegistered").value(1))
                .andExpect(jsonPath("$.summary.dealsWon").value(1))
                .andExpect(jsonPath("$.summary.wonValue").value(2000))
                .andExpect(jsonPath("$.from").exists());

        mockMvc.perform(get("/dashboard/activity").session(session))
                .andExpect(jsonPath("$.content[0].type").value("INTERACTION"))
                .andExpect(jsonPath("$.content[*].type", hasItem("STATUS_CHANGE")))
                .andExpect(jsonPath("$.content[*].companyName", hasItem("Loja Painel Contato")));

        mockMvc.perform(get("/dashboard/summary").param("from", "2026-02-01").param("to", "2026-01-01").session(session))
                .andExpect(status().isBadRequest());
        // A past period with no events: nothing in the period, but the per-stage snapshot is current.
        mockMvc.perform(get("/dashboard/summary").param("from", "2020-01-01").param("to", "2020-01-31").session(session))
                .andExpect(jsonPath("$.summary.companiesAdded").value(0))
                .andExpect(jsonPath("$.summary.dealsWon").value(0));
        mockMvc.perform(get("/dashboard/summary").param("segment", "OUTRO").session(session))
                .andExpect(jsonPath("$.summary.leadsByStatus", not(hasItem("WON"))));
    }

    /** The origin is the company's first provenance; a later import does not change it (D29). */
    @Test
    @DisplayName("the dashboard filters by the origin through which the company entered")
    void dashboardBySource() throws Exception {
        String leadId = createLead("Loja Origem Manual");
        // An import that later completes the company writes new provenance.
        jdbc.update("""
                insert into lead_source (id, organization_id, company_id, source_type, source_name, collected_at)
                select gen_random_uuid(), l.organization_id, l.company_id, 'CSV_IMPORT', 'Planilha ficticia', now()
                  from lead l where l.id = ?
                """, UUID.fromString(leadId));

        mockMvc.perform(get("/dashboard/summary").param("source", "MANUAL").session(session))
                .andExpect(jsonPath("$.summary.leadsByStatus.NEW").value(1))
                .andExpect(jsonPath("$.summary.companiesAdded").value(1));
        mockMvc.perform(get("/dashboard/summary").param("source", "CSV_IMPORT").session(session))
                .andExpect(jsonPath("$.summary.leadsByStatus.NEW").doesNotExist())
                .andExpect(jsonPath("$.summary.companiesAdded").value(0));
        mockMvc.perform(get("/dashboard/activity").param("source", "CSV_IMPORT").session(session))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/dashboard/summary").param("source", "PLANILHA").session(session))
                .andExpect(status().isBadRequest());
    }

    /** Time series: the same definitions as the summary, in Brasília calendar intervals. */
    @Test
    @DisplayName("the timeline fills empty intervals and respects the Brasília day")
    void timeline() throws Exception {
        String leadId = createLead("Loja Linha do Tempo");
        send(post("/leads/" + leadId + "/interactions"), interaction("CONTACT", "OUTBOUND", Instant.now()))
                .andExpect(status().isCreated());
        String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

        // Default: 30 days, one per day, the last of them today.
        mockMvc.perform(get("/dashboard/timeline").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("DAY"))
                .andExpect(jsonPath("$.points.length()").value(30))
                .andExpect(jsonPath("$.points[-1].start").value(today))
                .andExpect(jsonPath("$.points[-1].companiesAdded").value(1))
                .andExpect(jsonPath("$.points[-1].contactsRegistered").value(1))
                .andExpect(jsonPath("$.points[0].companiesAdded").value(0));

        // 02:00 UTC on the 10th is still the 9th in Brasília.
        jdbc.update("update company set created_at = '2026-03-10T02:00:00Z' where id = "
                + "(select company_id from lead where id = ?)", UUID.fromString(leadId));
        mockMvc.perform(get("/dashboard/timeline").param("from", "2026-03-08").param("to", "2026-03-10")
                        .session(session))
                .andExpect(jsonPath("$.points[*].start", contains("2026-03-08", "2026-03-09", "2026-03-10")))
                .andExpect(jsonPath("$.points[*].companiesAdded", contains(0, 1, 0)));

        // The first week starts at the beginning of the period, not on the preceding Monday.
        mockMvc.perform(get("/dashboard/timeline").param("from", "2026-03-04").param("to", "2026-03-17")
                        .param("granularity", "WEEK").session(session))
                .andExpect(jsonPath("$.points[*].start", contains("2026-03-04", "2026-03-09", "2026-03-16")))
                .andExpect(jsonPath("$.points[*].companiesAdded", contains(0, 1, 0)));

        mockMvc.perform(get("/dashboard/timeline").param("from", "2026-01-01").param("to", "2026-06-30")
                        .session(session))
                .andExpect(jsonPath("$.granularity").value("MONTH"))
                .andExpect(jsonPath("$.points.length()").value(6))
                .andExpect(jsonPath("$.points[2].companiesAdded").value(1));

        mockMvc.perform(get("/dashboard/timeline").param("granularity", "HOUR").session(session))
                .andExpect(status().isBadRequest());
    }

    /** RF-071: an explicit denominator, and no rate on a small sample. */
    @Test
    @DisplayName("conversion by origin and segment only marks a sufficient sample from the minimum up")
    void conversion() throws Exception {
        for (int i = 0; i < 10; i++) {
            String leadId = createLead("Loja Conversao " + i);
            if (i < 3) {
                send(post("/leads/" + leadId + "/interactions"), interaction("CONTACT", "OUTBOUND", Instant.now()))
                        .andExpect(status().isCreated());
            }
            if (i == 0) {
                move(leadId, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");
                send(put("/leads/" + leadId + "/deal"), "{\"estimatedValue\": 1500}").andExpect(status().isOk());
                move(leadId, "WON");
            }
        }
        String other = JsonPath.read(send(post("/companies"),
                "{\"name\": \"Oficina Conversao\", \"segment\": \"AUTO\", \"city\": \"Cidade Exemplo\", \"state\": \"MG\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        send(post("/leads"), "{\"companyId\": \"%s\"}".formatted(other)).andExpect(status().isCreated());

        mockMvc.perform(get("/dashboard/conversion").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").exists())
                .andExpect(jsonPath("$.conversion.minimumSample").value(10))
                .andExpect(jsonPath("$.conversion.bySource[0].key").value("MANUAL"))
                .andExpect(jsonPath("$.conversion.bySource[0].leads").value(11))
                .andExpect(jsonPath("$.conversion.bySource[0].contacted").value(3))
                .andExpect(jsonPath("$.conversion.bySource[0].won").value(1))
                .andExpect(jsonPath("$.conversion.bySegment[0].key").value("RETAIL"))
                .andExpect(jsonPath("$.conversion.bySegment[0].leads").value(10))
                .andExpect(jsonPath("$.conversion.bySegment[0].sufficientSample").value(true))
                .andExpect(jsonPath("$.conversion.bySegment[1].key").value("AUTO"))
                .andExpect(jsonPath("$.conversion.bySegment[1].sufficientSample").value(false));
        // Leads opened outside the period do not enter the denominator.
        mockMvc.perform(get("/dashboard/conversion").param("from", "2020-01-01").param("to", "2020-01-31").session(session))
                .andExpect(jsonPath("$.conversion.bySource").isEmpty());
    }
}
