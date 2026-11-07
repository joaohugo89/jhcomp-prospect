package com.jhcomp.prospect.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * CSV import through the API (RF-015, RF-014, RF-022; doc 10 §4).
 *
 * <p>Each test uses its own organisation. Fictitious data (doc 10 §10).
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("CSV import API")
class ImportApiIntegrationTest extends PostgresIntegrationTest {

    /** Headers as a Brazilian spreadsheet exports them: accents and semicolons. */
    private static final String SAMPLE = """
            Razão Social;Segmento;Município;UF;Site;Telefone;Faturamento
            Barbearia Um;BARBERSHOP;Cidade Exemplo;MG;barbearia-um.example.test;(35) 98888-0001;100
            Padaria Dois;BAKERY;Cidade Exemplo;mg;;35 3333-0002;200
            Sem Cidade;RETAIL;;MG;;;300
            Link Perigoso;RETAIL;Cidade Exemplo;MG;javascript:1,alert(1);;400
            Barbearia Um Filial;BARBERSHOP;Outra Cidade;SP;;+55 35 988880001;500
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private IdentityTestData.Member member;
    private MockHttpSession session;

    ImportApiIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        member = testData.createActiveOperator("importacao");
        session = TestSessions.login(mockMvc, member);
    }

    private ResultActions upload(String fileName, byte[] content, String... params) throws Exception {
        var request = multipart("/imports/csv")
                .file(new MockMultipartFile("file", fileName, "text/csv", content))
                .session(session)
                .with(csrf());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private String analyze(String csv) throws Exception {
        return JsonPath.read(upload("empresas.csv", csv.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions confirm(String importId, String body) throws Exception {
        return mockMvc.perform(post("/imports/" + importId + "/confirm")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String createExistingCompany(String json) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/companies")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** A spreadsheet with one row repeating the existing company's site. */
    private static final String MERGE_CSV = """
            nome;cidade;uf;site;telefone;segmento;nome fantasia
            Outro Nome;Outra Cidade;SP;existente.example.test;(35) 3333-1234;OUTRO;Fantasia Nova
            """;

    private int companies() {
        Integer count = jdbc.queryForObject(
                "select count(*) from company where organization_id = ?", Integer.class, member.organizationId());
        return count == null ? 0 : count;
    }

    @Test
    @DisplayName("the analysis recognises headers, classifies the rows and creates no company")
    void analysisDoesNotPersistCompanies() throws Exception {
        upload("empresas.csv", SAMPLE.getBytes(StandardCharsets.UTF_8), "sourceName", "Planilha de outubro")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ANALYZED"))
                .andExpect(jsonPath("$.totalRows").value(5))
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.rejectedRows").value(2))
                .andExpect(jsonPath("$.duplicateRows").value(1))
                .andExpect(jsonPath("$.sourceName").value("Planilha de outubro"))
                .andExpect(jsonPath("$.columnMapping.name").value("Razão Social"))
                .andExpect(jsonPath("$.columnMapping.city").value("Município"))
                // A column that is no company field is neither read nor stored.
                .andExpect(jsonPath("$.ignoredColumns", contains("Faturamento")));

        assertThat(companies()).isZero();
    }

    @Test
    @DisplayName("the report shows each row's error, with the field and the line number")
    void reportShowsErrorsPerRow() throws Exception {
        String importId = analyze(SAMPLE);

        mockMvc.perform(get("/imports/" + importId + "/report").param("status", "REJECTED").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].rowNumber").value(4))
                .andExpect(jsonPath("$.content[0].errors[0].field").value("city"))
                .andExpect(jsonPath("$.content[0].errors[0].message").value("must not be blank"))
                // The same rule as manual entry: a dangerous URL does not get in through a spreadsheet.
                .andExpect(jsonPath("$.content[1].rowNumber").value(5))
                .andExpect(jsonPath("$.content[1].errors[0].field").value("websiteUrl"));
    }

    @Test
    @DisplayName("a phone in a different format within the same file is a possible duplicate")
    void duplicateWithinFile() throws Exception {
        String importId = analyze(SAMPLE);

        mockMvc.perform(get("/imports/" + importId + "/report").param("status", "DUPLICATE").session(session))
                .andExpect(jsonPath("$.content[0].rowNumber").value(6))
                .andExpect(jsonPath("$.content[0].duplicateOfRowNumber").value(2))
                .andExpect(jsonPath("$.content[0].duplicateSignals", contains("PHONE")));
    }

    @Test
    @DisplayName("a row matching an already registered company is a possible duplicate")
    void duplicateOfExistingCompany() throws Exception {
        String existing = JsonPath.read(mockMvc.perform(post("/companies")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Já Cadastrada", "city": "Cidade Exemplo", "state": "MG",
                                 "websiteUrl": "https://www.ja-cadastrada.example.test"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        String importId = analyze("""
                nome;cidade;uf;site
                Nome Diferente;Outra Cidade;SP;ja-cadastrada.example.test
                """);

        mockMvc.perform(get("/imports/" + importId + "/report").session(session))
                .andExpect(jsonPath("$.content[0].status").value("DUPLICATE"))
                .andExpect(jsonPath("$.content[0].duplicateOfCompanyId").value(existing))
                .andExpect(jsonPath("$.content[0].duplicateSignals", contains("DOMAIN")));
    }

    @Test
    @DisplayName("confirmation imports the valid rows with a CSV origin, skips unchosen duplicates and opens leads")
    void confirmImportsValidRows() throws Exception {
        String importId = analyze(SAMPLE);

        confirm(importId, "{\"createLeads\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.importedRows").value(2))
                .andExpect(jsonPath("$.leadsCreated").value(2))
                .andExpect(jsonPath("$.completedAt").value(notNullValue()));

        assertThat(companies()).isEqualTo(2);

        mockMvc.perform(get("/imports/" + importId + "/report").session(session))
                .andExpect(jsonPath("$.content[*].status",
                        contains("IMPORTED", "IMPORTED", "REJECTED", "REJECTED", "SKIPPED")))
                .andExpect(jsonPath("$.content[0].companyId").value(notNullValue()))
                .andExpect(jsonPath("$.content[0].leadId").value(notNullValue()));

        // Provenance: every company knows which file and line it came from (RF-022).
        var source = jdbc.queryForMap("""
                select s.source_type, s.source_name, s.source_reference
                  from lead_source s join company c on c.id = s.company_id
                 where c.organization_id = ? and c.name = 'Padaria Dois' and s.lead_id is null
                """, member.organizationId());
        assertThat(source.get("source_type")).isEqualTo("CSV_IMPORT");
        assertThat(source.get("source_name")).isEqualTo("Importacao CSV: empresas.csv");
        assertThat((String) source.get("source_reference")).endsWith("linha 3");

        var lead = jdbc.queryForMap("""
                select l.status from lead l join company c on c.id = l.company_id
                 where c.organization_id = ? and c.name = 'Padaria Dois'
                """, member.organizationId());
        assertThat(lead.get("status")).isEqualTo("NEW");
    }

    @Test
    @DisplayName("a duplicate chosen by the operator is imported")
    void includedDuplicateIsImported() throws Exception {
        String importId = analyze(SAMPLE);

        confirm(importId, "{\"includeDuplicateRows\": [6]}")
                .andExpect(jsonPath("$.importedRows").value(3))
                .andExpect(jsonPath("$.leadsCreated").value(0));

        assertThat(companies()).isEqualTo(3);
    }

    @Test
    @DisplayName("it only accepts including rows that are possible duplicates")
    void cannotIncludeOtherRows() throws Exception {
        String importId = analyze(SAMPLE);

        confirm(importId, "{\"includeDuplicateRows\": [4]}")
                .andExpect(status().isBadRequest());

        // The refusal undoes the claim: the import keeps awaiting confirmation.
        mockMvc.perform(get("/imports/" + importId).session(session))
                .andExpect(jsonPath("$.status").value("ANALYZED"));
        assertThat(companies()).isZero();
    }

    @Test
    @DisplayName("the overview counts by status and the list filters by status, within the organisation only")
    void summaryAndStatusFilter() throws Exception {
        String pending = analyze(SAMPLE);
        String done = analyze("nome;cidade;uf\nLoja Resumo Um;Cidade Exemplo;MG\nLoja Resumo Dois;Cidade Exemplo;MG\n");
        confirm(done, "{}").andExpect(status().isOk());

        mockMvc.perform(get("/imports/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReview").value(1))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.undone").value(0))
                .andExpect(jsonPath("$.companiesImported").value(2));
        mockMvc.perform(get("/imports").param("status", "ANALYZED").session(session))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pending));
        mockMvc.perform(get("/imports").param("status", "QUALQUER").session(session))
                .andExpect(status().isBadRequest());

        var other = testData.createActiveAdmin("importacao-resumo-b");
        mockMvc.perform(get("/imports/summary").session(TestSessions.login(mockMvc, other)))
                .andExpect(jsonPath("$.pendingReview").value(0))
                .andExpect(jsonPath("$.companiesImported").value(0));
    }

    @Test
    @DisplayName("confirming twice does not import twice")
    void confirmTwice() throws Exception {
        String importId = analyze(SAMPLE);

        confirm(importId, "{}").andExpect(status().isOk());
        confirm(importId, "{}").andExpect(status().isConflict());

        assertThat(companies()).isEqualTo(2);
    }

    @Test
    @DisplayName("a company registered between analysis and confirmation does not become a silent duplicate")
    void recheckAtConfirmation() throws Exception {
        String importId = analyze("""
                nome;cidade;uf;telefone
                Loja Nova;Cidade Exemplo;MG;35 3333-9999
                """);

        mockMvc.perform(post("/companies")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Cadastrada Depois", "city": "Cidade Exemplo", "state": "MG", "phone": "(35) 3333-9999"}
                                """))
                .andExpect(status().isCreated());

        confirm(importId, "{}")
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.duplicateRows").value(1))
                .andExpect(jsonPath("$.validRows").value(0));

        mockMvc.perform(get("/imports/" + importId + "/report").session(session))
                .andExpect(jsonPath("$.content[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.content[0].duplicateSignals", contains("PHONE")));
    }

    /** Doc 10 §4: the same file imported again. */
    @Test
    @DisplayName("resending the same file is flagged and its rows become duplicates")
    void sameFileTwice() throws Exception {
        String first = analyze(SAMPLE);
        confirm(first, "{}").andExpect(status().isOk());

        upload("copia.csv", SAMPLE.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previousImportId").value(first))
                .andExpect(jsonPath("$.validRows").value(0))
                .andExpect(jsonPath("$.duplicateRows").value(3));
    }

    @Test
    @DisplayName("discarding erases the row values and prevents confirming afterwards")
    void discard() throws Exception {
        String importId = analyze(SAMPLE);

        mockMvc.perform(post("/imports/" + importId + "/discard").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));
        confirm(importId, "{}").andExpect(status().isConflict());

        Integer withValues = jdbc.queryForObject(
                "select count(*) from import_row where import_id = ? and name is not null",
                Integer.class, UUID.fromString(importId));
        assertThat(withValues).isZero();
        assertThat(companies()).isZero();
    }

    @Test
    @DisplayName("manual column mapping")
    void manualMapping() throws Exception {
        String csv = """
                Cliente;Local;Sigla
                Oficina Ficticia;Cidade Exemplo;MG
                """;

        upload("clientes.csv", csv.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Required fields without a column: name, city, state"));

        upload("clientes.csv", csv.getBytes(StandardCharsets.UTF_8),
                        "mapping", "{\"name\": \"Cliente\", \"city\": \"Local\", \"state\": \"Sigla\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validRows").value(1));

        upload("clientes.csv", csv.getBytes(StandardCharsets.UTF_8),
                        "mapping", "{\"name\": \"Cliente\", \"city\": \"Cliente\", \"state\": \"Sigla\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuses a spreadsheet that is not CSV, and a file that is not UTF-8")
    void rejectsInvalidFiles() throws Exception {
        upload("empresas.xlsx", new byte[] {0x50, 0x4B, 0x03, 0x04})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));

        upload("empresas.csv", "nome;cidade;uf\nSalão;Poços;MG\n".getBytes(StandardCharsets.ISO_8859_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("File must be UTF-8 encoded"));
    }

    @Test
    @DisplayName("lists the organisation's imports, newest first")
    void listsImports() throws Exception {
        String first = analyze(SAMPLE);
        String second = analyze("nome;cidade;uf\nA;B;MG\n");
        // The JVM clock on Windows has a resolution of up to ~16 ms: two
        // consecutive analyses can receive the same instant, and the tiebreak
        // by id is random.
        jdbc.update("UPDATE import_batch SET created_at = created_at - interval '1 hour' WHERE id = ?::uuid", first);

        mockMvc.perform(get("/imports").session(session))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(second, first)))
                .andExpect(jsonPath("$.content[*].status", containsInAnyOrder("ANALYZED", "ANALYZED")));
    }

    /** RF-023: completing never overwrites what is already on file. */
    @Test
    @DisplayName("completing an existing company fills only the empty fields and records the origin")
    void mergeFillsOnlyBlankFields() throws Exception {
        String existing = createExistingCompany("""
                {"name": "Empresa Existente", "segment": "ORIGINAL", "city": "Cidade Exemplo", "state": "MG",
                 "websiteUrl": "existente.example.test"}
                """);
        String importId = analyze(MERGE_CSV);

        confirm(importId, "{\"mergeDuplicateRows\": [2]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.mergedRows").value(1));

        // No new company; the existing one gained only what was missing.
        assertThat(companies()).isEqualTo(1);
        mockMvc.perform(get("/companies/" + existing).session(session))
                .andExpect(jsonPath("$.name").value("Empresa Existente"))
                .andExpect(jsonPath("$.city").value("Cidade Exemplo"))
                .andExpect(jsonPath("$.segment").value("ORIGINAL"))
                .andExpect(jsonPath("$.phone").value("(35) 3333-1234"))
                .andExpect(jsonPath("$.tradeName").value("Fantasia Nova"));

        mockMvc.perform(get("/imports/" + importId + "/report").session(session))
                .andExpect(jsonPath("$.content[0].status").value("MERGED"))
                .andExpect(jsonPath("$.content[0].companyId").value(existing))
                .andExpect(jsonPath("$.content[0].mergedFields", contains("tradeName", "phone")));

        mockMvc.perform(get("/companies/" + existing + "/sources").session(session))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].type", containsInAnyOrder("MANUAL", "CSV_IMPORT")));
    }

    @Test
    @DisplayName("completing with lead creation duplicates no open opportunity and does not fail the import")
    void mergeDoesNotDuplicateOpenLead() throws Exception {
        String existing = createExistingCompany("""
                {"name": "Empresa Existente", "city": "Cidade Exemplo", "state": "MG",
                 "websiteUrl": "existente.example.test"}
                """);
        mockMvc.perform(post("/leads")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\": \"%s\"}".formatted(existing)))
                .andExpect(status().isCreated());
        String importId = analyze(MERGE_CSV);

        confirm(importId, "{\"mergeDuplicateRows\": [2], \"createLeads\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mergedRows").value(1))
                .andExpect(jsonPath("$.leadsCreated").value(0));

        Integer leads = jdbc.queryForObject(
                "select count(*) from lead where company_id = ?", Integer.class, UUID.fromString(existing));
        assertThat(leads).isEqualTo(1);
    }

    @Test
    @DisplayName("a company archived after the analysis is not changed")
    void mergeIntoArchivedCompanyIsSkipped() throws Exception {
        String existing = createExistingCompany("""
                {"name": "Empresa Existente", "city": "Cidade Exemplo", "state": "MG",
                 "websiteUrl": "existente.example.test"}
                """);
        String importId = analyze(MERGE_CSV);
        mockMvc.perform(post("/companies/" + existing + "/archive").session(session).with(csrf()))
                .andExpect(status().isOk());

        confirm(importId, "{\"mergeDuplicateRows\": [2]}")
                .andExpect(jsonPath("$.mergedRows").value(0));

        mockMvc.perform(get("/companies/" + existing).session(session))
                .andExpect(jsonPath("$.phone").doesNotExist());
        mockMvc.perform(get("/imports/" + importId + "/report").session(session))
                .andExpect(jsonPath("$.content[0].status").value("SKIPPED"));
    }

    @Test
    @DisplayName("it only completes a duplicate of a registered company, and never alongside importing as new")
    void invalidMergeChoices() throws Exception {
        String importId = analyze(SAMPLE);

        // Row 6 duplicates row 2 of the file itself: there is no company to complete.
        confirm(importId, "{\"mergeDuplicateRows\": [6]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Rows do not duplicate an existing company and cannot be merged: [6]"));

        createExistingCompany("""
                {"name": "Empresa Existente", "city": "Cidade Exemplo", "state": "MG",
                 "websiteUrl": "existente.example.test"}
                """);
        String mergeable = analyze(MERGE_CSV);
        confirm(mergeable, "{\"mergeDuplicateRows\": [2], \"includeDuplicateRows\": [2]}")
                .andExpect(status().isBadRequest());

        assertThat(companies()).isEqualTo(1);
    }
}
