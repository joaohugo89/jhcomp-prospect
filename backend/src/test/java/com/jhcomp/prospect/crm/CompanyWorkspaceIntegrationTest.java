package com.jhcomp.prospect.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * Companies as a tool (D32): deleting what was entered by mistake, undoing an
 * import, merging a duplicate, the list with lead and activity, bulk editing
 * and duplicate-while-typing. Fictitious data.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Companies: delete, undo an import, merge and list")
class CompanyWorkspaceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private IdentityTestData.Member admin;
    private MockHttpSession session;

    CompanyWorkspaceIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = testData.createActiveAdmin("empresas-ws");
        session = TestSessions.login(mockMvc, admin);
    }

    private ResultActions send(MockHttpSession as, MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.session(as).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createCompany(MockHttpSession as, String name, String extraJson) throws Exception {
        return JsonPath.read(send(as, post("/companies"), """
                {"name": "%s", "city": "Cidade Exemplo", "state": "MG"%s}
                """.formatted(name, extraJson)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.id");
    }

    private String openLead(MockHttpSession as, String companyId) throws Exception {
        return JsonPath.read(send(as, post("/leads"), "{\"companyId\": \"%s\"}".formatted(companyId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions deleteCompany(MockHttpSession as, String companyId, String name) throws Exception {
        return send(as, delete("/companies/" + companyId), "{\"confirmationName\": \"%s\"}".formatted(name));
    }

    private int count(String table, String column, String id) {
        Integer value = jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?", Integer.class, UUID.fromString(id));
        return value == null ? 0 : value;
    }

    @Test
    @DisplayName("a clean company is really deleted, along with its open lead and origin, and only the id is audited")
    void deletesCleanCompany() throws Exception {
        String company = createCompany(session, "Loja Engano", "");
        String lead = openLead(session, company);

        mockMvc.perform(get("/companies/" + company + "/removal").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
        // A mistyped name does not delete.
        deleteCompany(session, company, "Outra Loja").andExpect(status().isBadRequest());
        deleteCompany(session, company, "  loja engano ").andExpect(status().isNoContent());

        assertThat(count("company", "id", company)).isZero();
        assertThat(count("lead", "id", lead)).isZero();
        assertThat(count("lead_source", "company_id", company)).isZero();
        assertThat(count("lead_status_change", "lead_id", lead)).isZero();
        var audit = jdbc.queryForMap(
                "select resource_id, detail from audit_event where action = 'COMPANY_DELETE' and resource_id = ?",
                company);
        assertThat(audit.get("detail")).isNull();
        mockMvc.perform(get("/companies/" + company).session(session)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a company with commercial activity is not deleted: archiving is the path")
    void refusesCompanyWithActivity() throws Exception {
        String withInteraction = createCompany(session, "Loja Conversa", "");
        String lead = openLead(session, withInteraction);
        send(session, post("/leads/" + lead + "/interactions"), """
                {"type": "NOTE", "channel": "WHATSAPP", "direction": "INTERNAL", "occurredAt": "%s",
                 "outcome": "Anotacao", "notes": "Ficticia"}
                """.formatted(Instant.now())).andExpect(status().isCreated());

        String moved = createCompany(session, "Loja Andou", "");
        String movedLead = openLead(session, moved);
        send(session, patch("/leads/" + movedLead + "/status"), "{\"status\": \"QUALIFIED\"}")
                .andExpect(status().isOk());

        for (String[] company : new String[][] {{withInteraction, "Loja Conversa"}, {moved, "Loja Andou"}}) {
            mockMvc.perform(get("/companies/" + company[0] + "/removal").session(session))
                    .andExpect(jsonPath("$.allowed").value(false))
                    .andExpect(jsonPath("$.blocker").value("HAS_ACTIVITY"));
            deleteCompany(session, company[0], company[1]).andExpect(status().isConflict());
        }
        assertThat(count("company", "id", withInteraction)).isOne();
    }

    @Test
    @DisplayName("an operator deletes what they registered for 7 days; afterwards, or someone else's, only an administrator")
    void creatorGraceAndAdministrator() throws Exception {
        IdentityTestData.Member operator = testData.addMember(admin.organizationId(), OrganizationRole.OPERATOR);
        MockHttpSession operatorSession = TestSessions.login(mockMvc, operator);
        String mine = createCompany(operatorSession, "Loja Do Operador", "");
        String byAdmin = createCompany(session, "Loja Do Admin", "");

        mockMvc.perform(get("/companies/" + mine + "/removal").session(operatorSession))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.allowedUntil").exists());
        mockMvc.perform(get("/companies/" + byAdmin + "/removal").session(operatorSession))
                .andExpect(jsonPath("$.blocker").value("NOT_ALLOWED"));
        deleteCompany(operatorSession, byAdmin, "Loja Do Admin").andExpect(status().isForbidden());

        jdbc.update("update company set created_at = now() - interval '8 days' where id = ?", UUID.fromString(mine));
        deleteCompany(operatorSession, mine, "Loja Do Operador").andExpect(status().isForbidden());
        // An administrator at any time, whoever the author.
        deleteCompany(session, mine, "Loja Do Operador").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a contact block survives the company being deleted")
    void suppressionSurvives() throws Exception {
        jdbc.update("""
                insert into suppression_entry (id, organization_id, identifier_type, identifier, reason, requested_at,
                                               created_by_user_id, created_at)
                values (?, ?, 'PHONE', '5535988880099', 'Pedido ficticio', now(), ?, now())
                """, UUID.randomUUID(), admin.organizationId(), admin.userId());
        String company = createCompany(session, "Loja Bloqueada", ", \"phone\": \"(35) 98888-0099\"");
        deleteCompany(session, company, "Loja Bloqueada").andExpect(status().isNoContent());
        Integer entries = jdbc.queryForObject("select count(*) from suppression_entry where organization_id = ?",
                Integer.class, admin.organizationId());
        assertThat(entries).isOne();
    }

    private String importCsv(MockHttpSession as, String csv) throws Exception {
        String importId = JsonPath.read(mockMvc.perform(multipart("/imports/csv")
                        .file(new MockMultipartFile("file", "empresas.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .session(as).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return importId;
    }

    @Test
    @DisplayName("undoing an import deletes those created without activity, keeps the others and the completed ones")
    void undoImport() throws Exception {
        String existing = createCompany(session, "Empresa Existente", ", \"websiteUrl\": \"https://existente.example.test\"");
        String importId = importCsv(session, """
                nome;cidade;uf;site;telefone
                Importada Limpa;Cidade Exemplo;MG;;(35) 3333-1001
                Importada Ativa;Cidade Exemplo;MG;;(35) 3333-1002
                Completa Existente;Outra Cidade;SP;existente.example.test;(35) 3333-1003
                """);
        // Before confirming there is nothing to undo.
        send(session, post("/imports/" + importId + "/undo"), "").andExpect(status().isConflict());
        send(session, post("/imports/" + importId + "/confirm"), "{\"mergeDuplicateRows\": [4], \"createLeads\": true}")
                .andExpect(status().isOk());
        String active = jdbc.queryForObject("""
                select r.lead_id::text from import_row r join company c on c.id = r.company_id
                 where r.import_id = ? and c.name = 'Importada Ativa'
                """, String.class, UUID.fromString(importId));
        send(session, patch("/leads/" + active + "/status"), "{\"status\": \"QUALIFIED\"}").andExpect(status().isOk());

        mockMvc.perform(get("/imports/" + importId + "/undo").session(session))
                .andExpect(jsonPath("$.removed").value(1))
                .andExpect(jsonPath("$.keptWithActivity").value(1))
                .andExpect(jsonPath("$.mergedUntouched").value(1));
        send(session, post("/imports/" + importId + "/undo"), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(1))
                .andExpect(jsonPath("$.undoneAt").exists());

        assertThat(jdbc.queryForList("select name from company where organization_id = ? order by name", String.class,
                admin.organizationId())).containsExactly("Empresa Existente", "Importada Ativa");
        assertThat(count("company", "id", existing)).isOne();
        // The row stays in the batch, without the company.
        mockMvc.perform(get("/imports/" + importId + "/report").param("status", "REMOVED").session(session))
                .andExpect(jsonPath("$.content[*].rowNumber", contains(2)))
                .andExpect(jsonPath("$.content[0].companyId").doesNotExist());
        mockMvc.perform(get("/imports/" + importId).session(session)).andExpect(jsonPath("$.undoneAt").exists());
        // Once only.
        send(session, post("/imports/" + importId + "/undo"), "").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("undoing somebody else's import, or one older than 7 days, requires an administrator")
    void undoImportPermissions() throws Exception {
        String importId = importCsv(session, "nome;cidade;uf\nImportada Admin;Cidade Exemplo;MG\n");
        send(session, post("/imports/" + importId + "/confirm"), "{}").andExpect(status().isOk());
        IdentityTestData.Member operator = testData.addMember(admin.organizationId(), OrganizationRole.OPERATOR);
        MockHttpSession operatorSession = TestSessions.login(mockMvc, operator);
        send(operatorSession, post("/imports/" + importId + "/undo"), "").andExpect(status().isForbidden());

        String own = importCsv(operatorSession, "nome;cidade;uf\nImportada Operador;Cidade Exemplo;MG\n");
        send(operatorSession, post("/imports/" + own + "/confirm"), "{}").andExpect(status().isOk());
        jdbc.update("update import_batch set completed_at = now() - interval '8 days' where id = ?", UUID.fromString(own));
        send(operatorSession, post("/imports/" + own + "/undo"), "").andExpect(status().isForbidden());
        send(session, post("/imports/" + own + "/undo"), "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("merging moves leads and origins, fills only empty fields and archives the source")
    void mergeDuplicates() throws Exception {
        String target = createCompany(session, "Barbearia Destino", ", \"phone\": \"(35) 3333-2000\"");
        String source = createCompany(session, "Barbearia Origem",
                ", \"phone\": \"(35) 3333-9999\", \"businessEmail\": \"contato@origem.example.test\"");
        String lead = openLead(session, source);

        send(session, post("/companies/" + target + "/merge"), "{\"sourceCompanyId\": \"%s\"}".formatted(source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedLeads").value(1))
                .andExpect(jsonPath("$.filledFields", contains("businessEmail")))
                // The target's phone is not overwritten.
                .andExpect(jsonPath("$.company.phone").value("(35) 3333-2000"))
                .andExpect(jsonPath("$.company.businessEmail").value("contato@origem.example.test"));

        assertThat(jdbc.queryForObject("select company_id::text from lead where id = ?", String.class,
                UUID.fromString(lead))).isEqualTo(target);
        assertThat(count("lead_source", "company_id", source)).isZero();
        mockMvc.perform(get("/companies/" + source).session(session)).andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get("/companies/" + target + "/leads").session(session))
                .andExpect(jsonPath("$.content[*].leadId", contains(lead)));
    }

    @Test
    @DisplayName("undoing the import of a merge's source does not delete the target, which existed before it")
    void undoAfterMergeKeepsTarget() throws Exception {
        String target = createCompany(session, "Destino Anterior", "");
        String importId = importCsv(session, "nome;cidade;uf\nOrigem Importada;Cidade Exemplo;MG\n");
        send(session, post("/imports/" + importId + "/confirm"), "{}").andExpect(status().isOk());
        String source = jdbc.queryForObject("select company_id::text from import_row where import_id = ?",
                String.class, UUID.fromString(importId));
        send(session, post("/companies/" + target + "/merge"), "{\"sourceCompanyId\": \"%s\"}".formatted(source))
                .andExpect(status().isOk());

        mockMvc.perform(get("/imports/" + importId + "/undo").session(session))
                .andExpect(jsonPath("$.removed").value(0));
        send(session, post("/imports/" + importId + "/undo"), "").andExpect(status().isOk());
        assertThat(count("company", "id", target)).isOne();
        // The row that created the source now points at a company it did not create.
        mockMvc.perform(get("/imports/" + importId + "/report").param("status", "MERGED").session(session))
                .andExpect(jsonPath("$.content[0].companyId").value(target));
    }

    @Test
    @DisplayName("merging does not transfer authorship: whoever registered the source gains no right to delete the target")
    void mergeDoesNotTransferAuthorship() throws Exception {
        IdentityTestData.Member operator = testData.addMember(admin.organizationId(), OrganizationRole.OPERATOR);
        MockHttpSession operatorSession = TestSessions.login(mockMvc, operator);
        // The source is written first: its provenance has a lower sequence than the target's.
        String source = createCompany(operatorSession, "Origem Do Operador", "");
        String target = createCompany(session, "Destino Do Admin", "");
        send(session, post("/companies/" + target + "/merge"), "{\"sourceCompanyId\": \"%s\"}".formatted(source))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select created_by_user_id from company where id = ?", UUID.class,
                UUID.fromString(target))).isEqualTo(admin.userId());
        mockMvc.perform(get("/companies/" + target + "/removal").session(operatorSession))
                .andExpect(jsonPath("$.allowed").value(false));
        deleteCompany(operatorSession, target, "Destino Do Admin").andExpect(status().is4xxClientError());
        // A merge target is never "clean", not even for an administrator.
        deleteCompany(session, target, "Destino Do Admin").andExpect(status().isConflict());
        assertThat(count("company", "id", target)).isOne();
        // The merged source does not go either, and cannot be merged again
        // into another target: either would erase the mark protecting the
        // target.
        deleteCompany(session, source, "Origem Do Operador").andExpect(status().isConflict());
        String other = createCompany(session, "Outro Destino", "");
        send(session, post("/companies/" + other + "/merge"), "{\"sourceCompanyId\": \"%s\"}".formatted(source))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select merged_into_company_id from company where id = ?", UUID.class,
                UUID.fromString(source))).isEqualTo(UUID.fromString(target));
    }

    @Test
    @DisplayName("two simultaneous merges of the same source: one stands, the other gets 409")
    void concurrentMergesOfSameSource() throws Exception {
        String source = createCompany(session, "Origem Disputada", "");
        String first = createCompany(session, "Destino Um", "");
        String second = createCompany(session, "Destino Dois", "");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (String target : new String[] {first, second}) {
                results.add(pool.submit(() -> {
                    start.await();
                    return send(session, post("/companies/" + target + "/merge"),
                            "{\"sourceCompanyId\": \"%s\"}".formatted(source)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            java.util.List<Integer> statuses = new java.util.ArrayList<>();
            for (var result : results) {
                statuses.add(result.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        UUID mergedInto = jdbc.queryForObject("select merged_into_company_id from company where id = ?", UUID.class,
                UUID.fromString(source));
        assertThat(mergedInto.toString()).isIn(first, second);
    }

    @Test
    @DisplayName("merging refuses two open cycles, the company itself and an archived target")
    void mergeRefusals() throws Exception {
        String target = createCompany(session, "Loja A", "");
        String source = createCompany(session, "Loja B", "");
        openLead(session, target);
        openLead(session, source);
        send(session, post("/companies/" + target + "/merge"), "{\"sourceCompanyId\": \"%s\"}".formatted(source))
                .andExpect(status().isConflict());
        send(session, post("/companies/" + target + "/merge"), "{\"sourceCompanyId\": \"%s\"}".formatted(target))
                .andExpect(status().isBadRequest());
        send(session, post("/companies/" + source + "/archive"), "").andExpect(status().isOk());
        send(session, post("/companies/" + source + "/merge"), "{\"sourceCompanyId\": \"%s\"}".formatted(target))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("the list carries current lead, contact, origin, activity and duplicate, with slices and ordering")
    void worklist() throws Exception {
        String withLead = createCompany(session, "Alfa Com Lead", ", \"phone\": \"(35) 3333-3001\"");
        String lead = openLead(session, withLead);
        send(session, patch("/leads/" + lead), "{\"assignedUserId\": \"%s\"}".formatted(admin.userId()))
                .andExpect(status().isOk());
        String twin = createCompany(session, "Beta Gemea", ", \"phone\": \"35 3333-3001\"");
        String bare = createCompany(session, "Gama Sem Contato", "");
        String archived = createCompany(session, "Delta Arquivada", "");
        send(session, post("/companies/" + archived + "/archive"), "").andExpect(status().isOk());

        mockMvc.perform(get("/companies/worklist").param("sort", "NAME").param("direction", "asc").session(session))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].companyId", contains(withLead, twin, bare)))
                .andExpect(jsonPath("$.content[0].currentLead.status").value("NEW"))
                .andExpect(jsonPath("$.content[0].currentLead.open").value(true))
                .andExpect(jsonPath("$.content[0].currentLead.assigneeName").value("Usuario de Teste"))
                .andExpect(jsonPath("$.content[0].hasPhone").value(true))
                .andExpect(jsonPath("$.content[0].origin").value("MANUAL"))
                .andExpect(jsonPath("$.content[0].lastActivityAt").exists())
                .andExpect(jsonPath("$.content[0].possibleDuplicate").value(true))
                .andExpect(jsonPath("$.content[2].currentLead").doesNotExist())
                .andExpect(jsonPath("$.content[2].possibleDuplicate").value(false));

        mockMvc.perform(get("/companies/worklist").param("focus", "WITHOUT_LEAD").session(session))
                .andExpect(jsonPath("$.content[*].companyId", containsInAnyOrder(twin, bare)));
        mockMvc.perform(get("/companies/worklist").param("focus", "WITHOUT_CONTACT").session(session))
                .andExpect(jsonPath("$.content[*].companyId", contains(bare)));
        mockMvc.perform(get("/companies/worklist").param("focus", "DUPLICATE").session(session))
                .andExpect(jsonPath("$.content[*].companyId", containsInAnyOrder(withLead, twin)));
        mockMvc.perform(get("/companies/worklist").param("focus", "ARCHIVED").session(session))
                .andExpect(jsonPath("$.content[*].companyId", contains(archived)));
        mockMvc.perform(get("/companies/worklist").param("direction", "sideways").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/companies/" + twin + "/summary").session(session))
                .andExpect(jsonPath("$.name").value("Beta Gemea"));
    }

    @Test
    @DisplayName("a bulk change sets the segment in the existing spelling and the city, skipping archived ones")
    void bulkUpdate() throws Exception {
        createCompany(session, "Ja Barbearia", ", \"segment\": \"Barbearia\"");
        String first = createCompany(session, "Loja Lote Um", "");
        String second = createCompany(session, "Loja Lote Dois", "");
        send(session, post("/companies/" + second + "/archive"), "").andExpect(status().isOk());

        send(session, post("/companies/bulk"), """
                {"companyIds": ["%s", "%s"], "segment": "barbearia"}
                """.formatted(first, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skippedArchived").value(1));
        mockMvc.perform(get("/companies/" + first).session(session)).andExpect(jsonPath("$.segment").value("Barbearia"));

        send(session, post("/companies/bulk"), "{\"companyIds\": [\"%s\"], \"city\": \"Nova Cidade\"}".formatted(first))
                .andExpect(status().isBadRequest());
        send(session, post("/companies/bulk"), """
                {"companyIds": ["%s"], "city": "Nova Cidade", "state": "sp"}
                """.formatted(first)).andExpect(status().isOk());
        mockMvc.perform(get("/companies/" + first).session(session))
                .andExpect(jsonPath("$.city").value("Nova Cidade"))
                .andExpect(jsonPath("$.state").value("SP"));
        send(session, post("/companies/bulk"), "{\"companyIds\": []}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("duplicate-while-typing uses phone, site and name in the city, without counting the company itself")
    void duplicateCheck() throws Exception {
        String existing = createCompany(session, "Padaria Sol",
                ", \"phone\": \"(35) 3333-4000\", \"websiteUrl\": \"https://padaria-sol.example.test\"");

        send(session, post("/companies/duplicate-check"), "{\"phone\": \"35 3333 4000\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyId").value(existing))
                .andExpect(jsonPath("$[0].signals", contains("PHONE")));
        send(session, post("/companies/duplicate-check"), """
                {"name": "padaria  sol", "city": "cidade exemplo", "state": "mg"}
                """).andExpect(jsonPath("$[0].signals", contains("NAME_CITY_STATE")));
        send(session, post("/companies/duplicate-check"), "{\"websiteUrl\": \"padaria-sol.example.test/contato\"}")
                .andExpect(jsonPath("$[0].signals", contains("DOMAIN")));
        // When editing the company itself, it is not a duplicate of itself.
        send(session, post("/companies/duplicate-check"), """
                {"phone": "(35) 3333-4000", "excludeCompanyId": "%s"}
                """.formatted(existing)).andExpect(jsonPath("$.length()").value(0));
        // A name alone, with no city, is not a signal.
        send(session, post("/companies/duplicate-check"), "{\"name\": \"Padaria Sol\"}")
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("another organisation: 404 on every new route, and neither list nor bulk can see it")
    void isolation() throws Exception {
        String company = createCompany(session, "Loja Isolada", ", \"phone\": \"(35) 3333-5000\"");
        String other = createCompany(session, "Loja Isolada Dois", "");
        String importId = importCsv(session, "nome;cidade;uf\nImportada Isolada;Cidade Exemplo;MG\n");
        send(session, post("/imports/" + importId + "/confirm"), "{}").andExpect(status().isOk());

        IdentityTestData.Member intruder = testData.createActiveAdmin("empresas-ws-b");
        MockHttpSession intruderSession = TestSessions.login(mockMvc, intruder);
        String own = createCompany(intruderSession, "Loja Do Intruso", "");

        mockMvc.perform(get("/companies/" + company + "/removal").session(intruderSession))
                .andExpect(status().isNotFound());
        deleteCompany(intruderSession, company, "Loja Isolada").andExpect(status().isNotFound());
        mockMvc.perform(get("/companies/" + company + "/summary").session(intruderSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/companies/" + company + "/leads").session(intruderSession))
                .andExpect(status().isNotFound());
        send(intruderSession, post("/companies/" + own + "/merge"), "{\"sourceCompanyId\": \"%s\"}".formatted(company))
                .andExpect(status().isNotFound());
        send(intruderSession, post("/companies/" + company + "/merge"), "{\"sourceCompanyId\": \"%s\"}".formatted(other))
                .andExpect(status().isNotFound());
        send(intruderSession, post("/companies/bulk"), "{\"companyIds\": [\"%s\"], \"segment\": \"Invasao\"}"
                .formatted(company)).andExpect(status().isNotFound());
        mockMvc.perform(get("/imports/" + importId + "/undo").session(intruderSession))
                .andExpect(status().isNotFound());
        send(intruderSession, post("/imports/" + importId + "/undo"), "").andExpect(status().isNotFound());
        mockMvc.perform(get("/companies/worklist").session(intruderSession))
                .andExpect(jsonPath("$.content[*].companyId", contains(own)));
        send(intruderSession, post("/companies/duplicate-check"), "{\"phone\": \"(35) 3333-5000\"}")
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(count("company", "id", company)).isOne();
        mockMvc.perform(get("/companies/" + company).session(session)).andExpect(jsonPath("$.segment").doesNotExist());
    }
}
