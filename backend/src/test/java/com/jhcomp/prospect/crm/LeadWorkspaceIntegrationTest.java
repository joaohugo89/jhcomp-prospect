package com.jhcomp.prospect.crm;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** Fila de trabalho, segmentos, acoes em lote e visoes salvas (D31). Dados ficticios. */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Leads: fila de trabalho, segmentos, lote e visoes")
class LeadWorkspaceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private IdentityTestData.Member admin;
    private MockHttpSession session;

    LeadWorkspaceIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = testData.createActiveAdmin("workspace");
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
                    "{\"status\": \"%s\", \"contactConfirmed\": true}".formatted(target)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("fila traz responsavel, valor, proxima tarefa e entrada na etapa, e ordena por eles")
    void worklistColumnsAndSort() throws Exception {
        String big = createLead(session, "Loja Valor Alto", "Barbearia");
        String small = createLead(session, "Loja Valor Baixo", "barbearia ");
        String none = createLead(session, "Loja Sem Valor", null);
        for (String lead : new String[] {big, small}) {
            move(lead, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "PROPOSAL_SENT");
        }
        send(session, put("/leads/" + big + "/deal"), "{\"estimatedValue\": 3000}").andExpect(status().isOk());
        send(session, put("/leads/" + small + "/deal"), "{\"estimatedValue\": 500}").andExpect(status().isOk());
        send(session, patch("/leads/" + small), "{\"assignedUserId\": \"%s\"}".formatted(admin.userId()))
                .andExpect(status().isOk());
        String task = JsonPath.read(send(session, post("/tasks"), "{\"leadId\": \"%s\", \"title\": \"Ligar\"}".formatted(small))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        jdbc.update("update task set due_at = now() - interval '1 day' where id = ?", UUID.fromString(task));

        mockMvc.perform(get("/leads/worklist").param("sort", "VALUE").param("direction", "desc").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                // Sem valor vai por ultimo, mesmo do maior para o menor.
                .andExpect(jsonPath("$.content[*].leadId", contains(big, small, none)))
                .andExpect(jsonPath("$.content[1].assigneeName").value("Usuario de Teste"))
                .andExpect(jsonPath("$.content[1].nextTaskTitle").value("Ligar"))
                .andExpect(jsonPath("$.content[1].nextTaskDueAt").exists())
                .andExpect(jsonPath("$.content[0].stageEnteredAt").exists())
                .andExpect(jsonPath("$.content[0].estimatedValue").value(3000.0));

        // Segmento sem distinguir caixa e espaco; sem segmento a parte.
        mockMvc.perform(get("/leads/worklist").param("segment", "BARBEARIA").session(session))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/leads/worklist").param("noSegment", "true").session(session))
                .andExpect(jsonPath("$.content[*].leadId", contains(none)));
        mockMvc.perform(get("/leads/worklist").param("focus", "OVERDUE").session(session))
                .andExpect(jsonPath("$.content[*].leadId", contains(small)));
        mockMvc.perform(get("/leads/worklist").param("focus", "WITHOUT_TASK").session(session))
                .andExpect(jsonPath("$.content[*].leadId", contains(big)));
        mockMvc.perform(get("/leads/worklist").param("assignee", admin.userId().toString()).session(session))
                .andExpect(jsonPath("$.content[*].leadId", contains(small)));
        mockMvc.perform(get("/leads/worklist").param("unassigned", "true").param("sort", "COMPANY")
                        .param("direction", "asc").session(session))
                .andExpect(jsonPath("$.content[*].leadId", contains(none, big)));

        jdbc.update("update lead_status_change set changed_at = now() - interval '10 days' where lead_id = ? "
                + "and recorded_seq = (select max(recorded_seq) from lead_status_change where lead_id = ?)",
                UUID.fromString(big), UUID.fromString(big));
        mockMvc.perform(get("/leads/worklist").param("focus", "STALE").session(session))
                .andExpect(jsonPath("$.content[*].leadId", contains(big)));

        mockMvc.perform(get("/leads/worklist").param("scoreMin", "50").param("scoreMax", "10").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/leads/worklist").param("direction", "sideways").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/leads/" + small + "/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadId").value(small))
                .andExpect(jsonPath("$.estimatedValue").value(500.0))
                .andExpect(jsonPath("$.nextTaskTitle").value("Ligar"));
        mockMvc.perform(get("/leads/worklist").param("page", "5").session(session))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("origem na fila e a primeira proveniencia, como no dashboard")
    void worklistOrigin() throws Exception {
        String lead = createLead(session, "Loja Manual Depois Importada", null);
        jdbc.update("""
                insert into lead_source (id, organization_id, company_id, source_type, source_name, collected_at)
                select gen_random_uuid(), l.organization_id, l.company_id, 'CSV_IMPORT', 'Planilha ficticia', now()
                  from lead l where l.id = ?
                """, UUID.fromString(lead));

        mockMvc.perform(get("/leads/worklist").param("source", "MANUAL").session(session))
                .andExpect(jsonPath("$.content[*].leadId", contains(lead)));
        mockMvc.perform(get("/leads/worklist").param("source", "CSV_IMPORT").session(session))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("segmentos em uso juntam grafias iguais e contam as empresas sem segmento")
    void segments() throws Exception {
        createLead(session, "Barbearia Um", "Barbearia");
        createLead(session, "Barbearia Dois", "Barbearia");
        createLead(session, "Barbearia Tres", " barbearia");
        createLead(session, "Padaria Um", "Padaria");
        createLead(session, "Sem Segmento", null);

        mockMvc.perform(get("/companies/segments").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments[0].name").value("Barbearia"))
                .andExpect(jsonPath("$.segments[0].companies").value(3))
                .andExpect(jsonPath("$.segments[1].name").value("Padaria"))
                .andExpect(jsonPath("$.segments.length()").value(2))
                .andExpect(jsonPath("$.withoutSegment").value(1));
        // A lista de empresas tambem filtra sem distinguir caixa.
        mockMvc.perform(get("/companies").param("segment", "BARBEARIA").session(session))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("lote atribui ou muda prioridade de todos ou de nenhum")
    void bulk() throws Exception {
        String one = createLead(session, "Loja Lote Um", null);
        String two = createLead(session, "Loja Lote Dois", null);
        IdentityTestData.Member operator = testData.addMember(admin.organizationId(), OrganizationRole.OPERATOR);

        send(session, post("/leads/bulk"), """
                {"leadIds": ["%s", "%s"], "action": "ASSIGN", "assignedUserId": "%s"}
                """.formatted(one, two, operator.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));
        send(session, post("/leads/bulk"), """
                {"leadIds": ["%s", "%s"], "action": "PRIORITY", "priority": "VERY_HIGH"}
                """.formatted(one, two)).andExpect(status().isOk());
        mockMvc.perform(get("/leads/" + two).session(session))
                .andExpect(jsonPath("$.assignedUserId").value(operator.userId().toString()))
                .andExpect(jsonPath("$.priority").value("VERY_HIGH"));

        // Um id desconhecido desfaz o lote inteiro.
        send(session, post("/leads/bulk"), """
                {"leadIds": ["%s", "%s"], "action": "PRIORITY", "priority": "LOW"}
                """.formatted(one, UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get("/leads/" + one).session(session)).andExpect(jsonPath("$.priority").value("VERY_HIGH"));

        send(session, post("/leads/bulk"), "{\"leadIds\": [\"%s\"], \"action\": \"PRIORITY\"}".formatted(one))
                .andExpect(status().isBadRequest());
        send(session, post("/leads/bulk"), "{\"leadIds\": [], \"action\": \"ASSIGN\"}").andExpect(status().isBadRequest());
        send(session, post("/leads/bulk"), "{\"leadIds\": [\"%s\"], \"action\": \"STATUS\"}".formatted(one))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("visoes salvas: criar, listar, nome unico, consulta so de URL e apagar so as proprias")
    void views() throws Exception {
        String id = JsonPath.read(send(session, post("/leads/views"),
                "{\"name\": \"Minhas propostas\", \"query\": \"status=PROPOSAL_SENT&city=Cidade+Exemplo\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/leads/views").session(session))
                .andExpect(jsonPath("$[*].name", contains("Minhas propostas")))
                .andExpect(jsonPath("$[0].query").value("status=PROPOSAL_SENT&city=Cidade+Exemplo"));
        send(session, post("/leads/views"), "{\"name\": \"minhas PROPOSTAS\", \"query\": \"\"}")
                .andExpect(status().isConflict());
        send(session, post("/leads/views"), "{\"name\": \"Script\", \"query\": \"<script>\"}")
                .andExpect(status().isBadRequest());

        IdentityTestData.Member colleague = testData.addMember(admin.organizationId(), OrganizationRole.OPERATOR);
        MockHttpSession colleagueSession = TestSessions.login(mockMvc, colleague);
        mockMvc.perform(get("/leads/views").session(colleagueSession)).andExpect(jsonPath("$").isEmpty());
        send(colleagueSession, delete("/leads/views/" + id), "").andExpect(status().isNotFound());

        send(session, delete("/leads/views/" + id), "").andExpect(status().isNoContent());
        mockMvc.perform(get("/leads/views").session(session)).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("fila, segmentos, lote e visoes de A nao alcancam B")
    void isolation() throws Exception {
        IdentityTestData.Member other = testData.createActiveAdmin("workspace-b");
        MockHttpSession sessionB = TestSessions.login(mockMvc, other);
        String leadB = createLead(sessionB, "Empresa de B", "SegmentoDeB");
        String viewB = JsonPath.read(send(sessionB, post("/leads/views"), "{\"name\": \"De B\", \"query\": \"\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/leads/" + leadB + "/summary").session(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/leads/worklist").session(session))
                .andExpect(jsonPath("$.content[*].leadId", not(hasItem(leadB))))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/companies/segments").session(session))
                .andExpect(jsonPath("$.segments").isEmpty());
        send(session, post("/leads/bulk"), "{\"leadIds\": [\"%s\"], \"action\": \"PRIORITY\", \"priority\": \"LOW\"}"
                .formatted(leadB)).andExpect(status().isNotFound());
        mockMvc.perform(get("/leads/" + leadB).session(sessionB)).andExpect(jsonPath("$.priority").value("MEDIUM"));
        send(session, delete("/leads/views/" + viewB), "").andExpect(status().isNotFound());
        mockMvc.perform(get("/leads/views").session(sessionB)).andExpect(jsonPath("$[*].name", contains("De B")));
    }
}
