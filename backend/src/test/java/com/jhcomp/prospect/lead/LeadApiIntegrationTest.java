package com.jhcomp.prospect.lead;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
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

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.domain.OrganizationStatus;
import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationEntity;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** Leads through the API (RF-011, RF-012, RF-013; doc 06 §2). Fictitious data. */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Lead API")
class LeadApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private OrganizationEntity organization;
    private IdentityTestData.Member operator;
    private MockHttpSession session;

    LeadApiIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

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
    @DisplayName("a lead starts with a status, an origin and a creation date (RF-011's acceptance criterion)")
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
                // No analysis, no score: null, never zero (D7).
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
    @DisplayName("an absent priority becomes MEDIUM")
    void defaultPriority() throws Exception {
        createLead("""
                {"companyId": "%s"}
                """.formatted(createCompany("Loja Ficticia")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("MEDIUM"));
    }

    @Test
    @DisplayName("one open opportunity per company (doc 06 §3)")
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
    @DisplayName("a new commercial cycle is allowed once the previous one is closed")
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
    @DisplayName("an archived company receives no opportunity")
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
    @DisplayName("a non-existent company answers 404")
    void unknownCompany() throws Exception {
        createLead("""
                {"companyId": "00000000-0000-4000-8000-000000000000"}
                """)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a valid transition records the actor, the date and the previous state")
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

    /**
     * A tie on the instant follows the recording order, not the random id.
     *
     * <p>The tie is forced in the database: relying on the clock would make
     * the test flaky, which is exactly how the problem showed up.
     */
    @Test
    @DisplayName("events at the same instant come out in the order they were written")
    void tiesFollowRecordingOrder() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));
        changeStatus(leadId, "{\"status\": \"QUALIFIED\"}").andExpect(status().isOk());

        OffsetDateTime sameInstant = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        UUID lead = UUID.fromString(leadId);
        jdbc.update("update lead_status_change set changed_at = ? where lead_id = ?", sameInstant, lead);
        jdbc.update("update lead_source set collected_at = ? where lead_id = ?", sameInstant, lead);

        mockMvc.perform(get("/leads/" + leadId + "/timeline").session(session))
                .andExpect(jsonPath("$.content[0].toStatus").value("QUALIFIED"))
                .andExpect(jsonPath("$.content[1].type").value("SOURCE"))
                .andExpect(jsonPath("$.content[2].toStatus").value("NEW"));
    }

    @Test
    @DisplayName("a transition that is not allowed answers 409 and leaves the lead unchanged")
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

    /** Doc 06 §2: CONTACTED only with the operator's confirmation. */
    @Test
    @DisplayName("CONTACTED requires explicit confirmation that the contact happened")
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
    @DisplayName("a terminal state records a closing date and has no way out")
    void terminalState() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        changeStatus(leadId, "{\"status\": \"LOST\"}")
                .andExpect(jsonPath("$.closedAt").value(notNullValue()))
                .andExpect(jsonPath("$.allowedTransitions").isEmpty());

        changeStatus(leadId, "{\"status\": \"NEW\"}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DO_NOT_CONTACT is reachable and is not reverted by a status change")
    void doNotContactIsSticky() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));
        changeStatus(leadId, "{\"status\": \"QUALIFIED\"}").andExpect(status().isOk());

        changeStatus(leadId, "{\"status\": \"DO_NOT_CONTACT\", \"reason\": \"Pedido do titular\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedAt").doesNotExist());

        changeStatus(leadId, "{\"status\": \"QUALIFIED\"}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("re-sending the same status produces no event in the history")
    void sameStatusIsNoOp() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        changeStatus(leadId, "{\"status\": \"NEW\"}").andExpect(status().isOk());

        mockMvc.perform(get("/leads/" + leadId + "/timeline").session(session))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("an invalid status in the body answers 400")
    void unknownStatusValue() throws Exception {
        String leadId = createLeadFor(createCompany("Loja Ficticia"));

        changeStatus(leadId, "{\"status\": \"ARCHIVED\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("changes the priority")
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
    @DisplayName("assigns to an active member of the organisation and clears with null")
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
    @DisplayName("does not assign to a revoked member")
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
    @DisplayName("filters by status, company search and origin")
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
    @DisplayName("a score filter excludes leads with no analysis")
    void scoreFilterExcludesUnscored() throws Exception {
        createLeadFor(createCompany("Loja Ficticia"));

        mockMvc.perform(get("/leads").param("scoreMin", "0").session(session))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/leads").param("scoreMin", "50").param("scoreMax", "10").session(session))
                .andExpect(status().isBadRequest());
    }
}
