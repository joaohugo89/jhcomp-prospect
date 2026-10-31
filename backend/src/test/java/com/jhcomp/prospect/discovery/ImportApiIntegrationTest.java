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
 * Importacao CSV pela API (RF-015, RF-014, RF-022; doc 10 §4).
 *
 * <p>Cada teste usa uma organizacao propria. Dados ficticios (doc 10 §10).
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("API de importacao CSV")
class ImportApiIntegrationTest extends PostgresIntegrationTest {

    /** Cabecalhos como uma planilha brasileira exporta: acento, ponto e virgula. */
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

    /** Planilha com uma linha que repete o site da empresa existente. */
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
    @DisplayName("analise reconhece cabecalhos, classifica as linhas e nao cria empresa")
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
                // Coluna que nao e campo da empresa nao e lida nem guardada.
                .andExpect(jsonPath("$.ignoredColumns", contains("Faturamento")));

        assertThat(companies()).isZero();
    }

    @Test
    @DisplayName("relatorio mostra o erro de cada linha, com campo e numero da linha")
    void reportShowsErrorsPerRow() throws Exception {
        String importId = analyze(SAMPLE);

        mockMvc.perform(get("/imports/" + importId + "/report").param("status", "REJECTED").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].rowNumber").value(4))
                .andExpect(jsonPath("$.content[0].errors[0].field").value("city"))
                .andExpect(jsonPath("$.content[0].errors[0].message").value("must not be blank"))
                // A mesma regra do cadastro manual: URL perigosa nao passa por planilha.
                .andExpect(jsonPath("$.content[1].rowNumber").value(5))
                .andExpect(jsonPath("$.content[1].errors[0].field").value("websiteUrl"));
    }

    @Test
    @DisplayName("telefone com formatacao diferente no mesmo arquivo e possivel duplicata")
    void duplicateWithinFile() throws Exception {
        String importId = analyze(SAMPLE);

        mockMvc.perform(get("/imports/" + importId + "/report").param("status", "DUPLICATE").session(session))
                .andExpect(jsonPath("$.content[0].rowNumber").value(6))
                .andExpect(jsonPath("$.content[0].duplicateOfRowNumber").value(2))
                .andExpect(jsonPath("$.content[0].duplicateSignals", contains("PHONE")));
    }

    @Test
    @DisplayName("linha igual a empresa ja cadastrada e possivel duplicata")
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
    @DisplayName("confirmacao importa validas com origem CSV, pula duplicatas nao escolhidas e abre leads")
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

        // Proveniencia: cada empresa sabe de que arquivo e linha veio (RF-022).
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
    @DisplayName("duplicata escolhida pelo operador e importada")
    void includedDuplicateIsImported() throws Exception {
        String importId = analyze(SAMPLE);

        confirm(importId, "{\"includeDuplicateRows\": [6]}")
                .andExpect(jsonPath("$.importedRows").value(3))
                .andExpect(jsonPath("$.leadsCreated").value(0));

        assertThat(companies()).isEqualTo(3);
    }

    @Test
    @DisplayName("so aceita incluir linhas que sao possiveis duplicatas")
    void cannotIncludeOtherRows() throws Exception {
        String importId = analyze(SAMPLE);

        confirm(importId, "{\"includeDuplicateRows\": [4]}")
                .andExpect(status().isBadRequest());

        // A recusa desfaz a tomada: a importacao continua aguardando.
        mockMvc.perform(get("/imports/" + importId).session(session))
                .andExpect(jsonPath("$.status").value("ANALYZED"));
        assertThat(companies()).isZero();
    }

    @Test
    @DisplayName("confirmar duas vezes nao importa em dobro")
    void confirmTwice() throws Exception {
        String importId = analyze(SAMPLE);

        confirm(importId, "{}").andExpect(status().isOk());
        confirm(importId, "{}").andExpect(status().isConflict());

        assertThat(companies()).isEqualTo(2);
    }

    @Test
    @DisplayName("empresa cadastrada entre analise e confirmacao nao vira duplicata silenciosa")
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

    /** Doc 10 §4: importacao repetida do mesmo arquivo. */
    @Test
    @DisplayName("reenvio do mesmo arquivo e avisado e suas linhas viram duplicatas")
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
    @DisplayName("descarte apaga os valores das linhas e impede confirmar depois")
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
    @DisplayName("mapeamento manual de colunas")
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
    @DisplayName("recusa planilha que nao e CSV e arquivo fora de UTF-8")
    void rejectsInvalidFiles() throws Exception {
        upload("empresas.xlsx", new byte[] {0x50, 0x4B, 0x03, 0x04})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));

        upload("empresas.csv", "nome;cidade;uf\nSalão;Poços;MG\n".getBytes(StandardCharsets.ISO_8859_1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("File must be UTF-8 encoded"));
    }

    @Test
    @DisplayName("lista importacoes da organizacao, mais recente primeiro")
    void listsImports() throws Exception {
        String first = analyze(SAMPLE);
        String second = analyze("nome;cidade;uf\nA;B;MG\n");
        // O relogio da JVM no Windows tem resolucao de ate ~16 ms: duas analises
        // seguidas podem receber o mesmo instante, e o desempate por id e aleatorio.
        jdbc.update("UPDATE import_batch SET created_at = created_at - interval '1 hour' WHERE id = ?::uuid", first);

        mockMvc.perform(get("/imports").session(session))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", contains(second, first)))
                .andExpect(jsonPath("$.content[*].status", containsInAnyOrder("ANALYZED", "ANALYZED")));
    }

    /** RF-023: completar nunca sobrescreve o que ja esta cadastrado. */
    @Test
    @DisplayName("completar empresa existente preenche so campos vazios e registra a origem")
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

        // Nenhuma empresa nova; a existente ganhou so o que faltava.
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
    @DisplayName("completar com abertura de leads nao duplica oportunidade aberta nem derruba a importacao")
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
    @DisplayName("empresa arquivada depois da analise nao e alterada")
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
    @DisplayName("so completa duplicata de empresa cadastrada, e nao junto com importar como nova")
    void invalidMergeChoices() throws Exception {
        String importId = analyze(SAMPLE);

        // Linha 6 duplica a linha 2 do proprio arquivo: nao ha empresa a completar.
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
