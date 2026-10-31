package com.jhcomp.prospect.crm;

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
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** CRM e dashboard pela API (RF-040 a RF-043, RF-070, D2, D25). Dados ficticios. */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("API de CRM e dashboard")
class CrmApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private MockHttpSession session;

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
    @DisplayName("etapas sao os status do lead, em ordem, sem o bloqueio de contato")
    void stages() throws Exception {
        mockMvc.perform(get("/pipeline-stages").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].key", contains("NEW", "QUALIFIED", "CONTACT_APPROVED", "CONTACTED",
                        "IN_CONVERSATION", "PROPOSAL_SENT", "WON", "LOST")))
                .andExpect(jsonPath("$[6].terminal").value(true));
    }

    @Test
    @DisplayName("quadro traz os cards por etapa com ultima interacao e proxima tarefa")
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

    /** D2: deal nasce na proposta e o resultado vem do status do lead. */
    @Test
    @DisplayName("deal nasce na proposta, guarda valor em decimal e motivo de perda")
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
    @DisplayName("ganho sem passar por proposta tambem cria o deal")
    void wonWithoutProposal() throws Exception {
        String leadId = createLead("Loja Ganha");
        move(leadId, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "WON");

        mockMvc.perform(get("/leads/" + leadId + "/deal").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("WON"));
    }

    @Test
    @DisplayName("valor negativo ou com mais de duas casas e recusado")
    void dealValueValidation() throws Exception {
        String leadId = createLead("Loja Valor");
        move(leadId, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");

        send(put("/leads/" + leadId + "/deal"), "{\"estimatedValue\": -1}").andExpect(status().isBadRequest());
        send(put("/leads/" + leadId + "/deal"), "{\"estimatedValue\": 10.123}").andExpect(status().isBadRequest());
    }

    /** Regra 6: registrar contato nao move o lead para CONTACTED. */
    @Test
    @DisplayName("interacao registrada nao muda o status e aparece no historico")
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
    @DisplayName("interacao no futuro e recusada")
    void futureInteraction() throws Exception {
        String leadId = createLead("Loja Futuro");

        send(post("/leads/" + leadId + "/interactions"), interaction("NOTE", "INTERNAL", Instant.now().plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isBadRequest());
    }

    /** Doc 06 §9 e regra 4: pedido de nao contato vale na hora e bloqueia abordagem. */
    @Test
    @DisplayName("pedido de nao contato bloqueia o lead e recusa nova abordagem")
    void doNotContactRequest() throws Exception {
        String leadId = createLead("Loja Bloqueada");
        move(leadId, "QUALIFIED");

        send(post("/leads/" + leadId + "/interactions"), interaction("DO_NOT_CONTACT_REQUEST", "INBOUND", Instant.now()))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/leads/" + leadId).session(session)).andExpect(jsonPath("$.status").value("DO_NOT_CONTACT"));

        send(post("/leads/" + leadId + "/interactions"), interaction("CONTACT", "OUTBOUND", Instant.now()))
                .andExpect(status().isConflict());
        // A empresa ainda pode nos procurar: registrar o que ela fez continua possivel.
        send(post("/leads/" + leadId + "/interactions"), interaction("REPLY", "INBOUND", Instant.now()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("tarefa: cria, conclui e lista abertas primeiro")
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
    @DisplayName("dashboard conta leads por etapa, contatos e ganhos do periodo")
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
        // Periodo passado sem eventos: nada no periodo, mas a foto por etapa e atual.
        mockMvc.perform(get("/dashboard/summary").param("from", "2020-01-01").param("to", "2020-01-31").session(session))
                .andExpect(jsonPath("$.summary.companiesAdded").value(0))
                .andExpect(jsonPath("$.summary.dealsWon").value(0));
        mockMvc.perform(get("/dashboard/summary").param("segment", "OUTRO").session(session))
                .andExpect(jsonPath("$.summary.leadsByStatus", not(hasItem("WON"))));
    }
}
