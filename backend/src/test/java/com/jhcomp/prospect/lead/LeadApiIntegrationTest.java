package com.jhcomp.prospect.lead;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.domain.OrganizationStatus;
import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationEntity;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** Leads pela API (RF-011, RF-012, RF-013; doc 06 §2). Dados ficticios. */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("API de leads")
class LeadApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private OrganizationEntity organization;
    private IdentityTestData.Member operator;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        organization = testData.createOrganization("leads", OrganizationStatus.ACTIVE);
        operator = testData.createMember(organization, OrganizationRole.OPERATOR, UserStatus.ACTIVE, MemberStatus.ACTIVE);
        session = TestSessions.login(mockMvc, operator);
    }

    private String json(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private String createCompany(String name) throws Exception {
        return JsonPath.read(json(mockMvc.perform(post("/companies")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "segment": "BARBERSHOP", "city": "Cidade Exemplo", "state": "MG"}
                                """.formatted(name)))
                .andExpect(status().isCreated())), "$.id");
    }

    private ResultActions createLead(String body) throws Exception {
        return mockMvc.perform(post("/leads")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String createLeadFor(String companyId) throws Exception {
        return JsonPath.read(json(createLead("""
                        {"companyId": "%s"}
                        """.formatted(companyId))
                .andExpect(status().isCreated())), "$.id");
    }

    private ResultActions changeStatus(String leadId, String body) throws Exception {
        return mockMvc.perform(patch("/leads/" + leadId + "/status")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("lead nasce com status, origem e data de criacao (aceite do RF-011)")
    void createdLeadHasStatusSourceAndDate() throws Exception {
        String companyId = createCompany("Barbearia Ficticia");

        String leadId = JsonPath.read(json(createLead("""
                        {"companyId": "%s", "priority": "HIGH",
                         "source": {"name": "Indicacao de cliente", "reference": "Cliente ficticio"}}
                        """.formatted(companyId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.company.id").value(companyId))
                .andExpect(jsonPath("$.company.name").value("Barbearia Ficticia"))
                // Sem analise, sem score: nulo, nunca zero (D7).
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.allowedTransitions", contains("QUALIFIED", "LOST", "DO_NOT_CONTACT")))),
                "$.id");

        mockMvc.perform(get("/leads/" + leadId + "/timeline").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.type == 'STATUS_CHANGE')].toStatus").value("NEW"))
                .andExpect(jsonPath("$.content[?(@.type == 'STATUS_CHANGE')].actorName").value("Usuario de Teste"))
                .andExpect(jsonPath("$.content[?(@.type == 'SOURCE')].sourceName").value("Indicacao de cliente"));
    }

    @Test
    @DisplayName("prioridade ausente vira MEDIUM")
    void defaultPriority() throws Exception {
        createLead("""
                {"companyId": "%s"}
                """.formatted(createCompany("Loja Ficticia")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    @DisplayName("uma oportunidade aberta por empresa (doc 06 §3)")
    void oneOpenLeadPerCompany() throws Exception {
        String companyId = createCompany("Loja Ficticia");
        createLeadFor(companyId);

        createLead("""
                {"companyId": "%s"}
                """.formatted(companyId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("CONFLICT"));
    }

    @Test
    @DisplayName("novo ciclo comercial e permitido depois de encerrar o anterior")
    void newCycleAfterClosing() throws Exception {
        String companyId = createCompany("Loja Ficticia");
        String leadId = createLeadFor(companyId);

        changeStatus(leadId, """
                {"status": "LOST", "reason": "Sem interesse no momento"}
                """).andExpect(status().isOk());

        createLead("""
                {"companyId": "%s"}
                """.formatted(companyId))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("empresa arquivada nao recebe oportunidade")
    void archivedCompanyRejectsLead() throws Exception {
        String companyId = createCompany("Loja Arquivada");
        mockMvc.perform(post("/companies/" + companyId + "/archive").session(session).with(csrf()))
                .andExpect(status().isOk());

        createLead("""
                {"companyId": "%s"}
                """.formatted(companyId))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("empresa inexistente responde 404")
    void unknownCompany() throws Exception {
        createLead("""
                {"companyId": "00000000-0000-4000-8000-000000000000"}
                """)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("transicao valida registra ator, data e estado anterior")
    void validTransitionIsRecorded() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        changeStatus(leadId, """
                {"status": "QUALIFIED", "reason": "Perfil compativel"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"));

        mockMvc.perform(get("/leads/" + leadId + "/timeline").session(session))
                .andExpect(jsonPath("$.content[0].type").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$.content[0].fromStatus").value("NEW"))
                .andExpect(jsonPath("$.content[0].toStatus").value("QUALIFIED"))
                .andExpect(jsonPath("$.content[0].reason").value("Perfil compativel"))
                .andExpect(jsonPath("$.content[0].occurredAt").value(notNullValue()));
    }

    @Test
    @DisplayName("transicao nao permitida responde 409 e nao altera o lead")
    void invalidTransition() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        changeStatus(leadId, """
                {"status": "WON"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("CONFLICT"));

        mockMvc.perform(get("/leads/" + leadId).session(session))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    /** Doc 06 §2: CONTACTED so com confirmacao do operador. */
    @Test
    @DisplayName("CONTACTED exige confirmacao explicita de que o contato ocorreu")
    void contactedRequiresConfirmation() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));
        changeStatus(leadId, "{\"status\": \"QUALIFIED\"}").andExpect(status().isOk());
        changeStatus(leadId, "{\"status\": \"CONTACT_APPROVED\"}").andExpect(status().isOk());

        changeStatus(leadId, "{\"status\": \"CONTACTED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/leads/" + leadId).session(session))
                .andExpect(jsonPath("$.status").value("CONTACT_APPROVED"));

        changeStatus(leadId, "{\"status\": \"CONTACTED\", \"contactConfirmed\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"));
    }

    @Test
    @DisplayName("estado terminal registra data de fechamento e nao tem saida")
    void terminalState() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        changeStatus(leadId, "{\"status\": \"LOST\"}")
                .andExpect(jsonPath("$.closedAt").value(notNullValue()))
                .andExpect(jsonPath("$.allowedTransitions").isEmpty());

        changeStatus(leadId, "{\"status\": \"NEW\"}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DO_NOT_CONTACT e alcancavel e nao e revertido por mudanca de status")
    void doNotContactIsSticky() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));
        changeStatus(leadId, "{\"status\": \"QUALIFIED\"}").andExpect(status().isOk());

        changeStatus(leadId, "{\"status\": \"DO_NOT_CONTACT\", \"reason\": \"Pedido do titular\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedAt").doesNotExist());

        changeStatus(leadId, "{\"status\": \"QUALIFIED\"}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("reenviar o mesmo status nao gera evento no historico")
    void sameStatusIsNoOp() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        changeStatus(leadId, "{\"status\": \"NEW\"}").andExpect(status().isOk());

        mockMvc.perform(get("/leads/" + leadId + "/timeline").session(session))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("status invalido no corpo responde 400")
    void unknownStatusValue() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        changeStatus(leadId, "{\"status\": \"ARCHIVED\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("altera prioridade")
    void changePriority() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        mockMvc.perform(patch("/leads/" + leadId + "/priority")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"VERY_HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("VERY_HIGH"));
    }

    @Test
    @DisplayName("atribui a membro ativo da organizacao e remove com null")
    void assignAndUnassign() throws Exception {
        var colleague = testData.createMember(
                organization, OrganizationRole.OPERATOR, UserStatus.ACTIVE, MemberStatus.ACTIVE);
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        mockMvc.perform(patch("/leads/" + leadId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUserId\": \"%s\"}".formatted(colleague.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedUserId").value(colleague.userId().toString()));

        mockMvc.perform(patch("/leads/" + leadId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUserId\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedUserId").doesNotExist());
    }

    @Test
    @DisplayName("nao atribui a membro revogado")
    void cannotAssignRevokedMember() throws Exception {
        var revoked = testData.createMember(
                organization, OrganizationRole.OPERATOR, UserStatus.ACTIVE, MemberStatus.REVOKED);
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        mockMvc.perform(patch("/leads/" + leadId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedUserId\": \"%s\"}".formatted(revoked.userId())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("filtra por status, busca de empresa e origem")
    void listingFilters() throws Exception {
        String qualified = createLeadFor(createCompany("Salão Ficticio"));
        createLeadFor(createCompany("Mercado Ficticio"));
        changeStatus(qualified, "{\"status\": \"QUALIFIED\"}").andExpect(status().isOk());

        mockMvc.perform(get("/leads").param("status", "QUALIFIED").session(session))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(qualified));

        mockMvc.perform(get("/leads").param("search", "salao").session(session))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].company.name").value("Salão Ficticio"));

        mockMvc.perform(get("/leads").param("source", "MANUAL").session(session))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/leads").param("source", "CSV_IMPORT").session(session))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("filtro de score nao inclui lead sem analise")
    void scoreFilterExcludesUnscored() throws Exception {
        createLeadFor(createCompany("Loja Ficticia"));

        mockMvc.perform(get("/leads").param("scoreMin", "0").session(session))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/leads").param("scoreMin", "50").param("scoreMax", "10").session(session))
                .andExpect(status().isBadRequest());
    }
}
