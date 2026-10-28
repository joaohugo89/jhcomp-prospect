package com.jhcomp.prospect.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * Importacoes mantem o escopo da organizacao (doc 10 §3).
 *
 * <p>Usuario de A conhece o id de uma importacao de B e tenta todas as rotas.
 * Tudo responde 404, e o banco confirma que nada de B mudou.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Isolamento entre organizacoes: importacao")
class ImportIsolationTest extends PostgresIntegrationTest {

    private static final String CSV = """
            nome;cidade;uf;site
            Empresa Ficticia;Cidade Exemplo;MG;compartilhado.example.test
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private MockHttpSession sessionA;
    private MockHttpSession sessionB;
    private String importB;

    ImportIsolationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        sessionA = TestSessions.login(mockMvc, testData.createActiveAdmin("imp-a"));
        sessionB = TestSessions.login(mockMvc, testData.createActiveAdmin("imp-b"));
        importB = analyze(sessionB);
    }

    private String analyze(MockHttpSession session) throws Exception {
        return JsonPath.read(mockMvc.perform(multipart("/imports/csv")
                        .file(new MockMultipartFile("file", "empresas.csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8)))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void expectNotFound(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.session(sessionA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("usuario de A nao ve, nao confirma e nao descarta importacao de B")
    void routesAreScoped() throws Exception {
        expectNotFound(get("/imports/" + importB));
        expectNotFound(get("/imports/" + importB + "/report"));
        expectNotFound(post("/imports/" + importB + "/confirm"));
        expectNotFound(post("/imports/" + importB + "/discard"));

        mockMvc.perform(get("/imports").session(sessionA))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(importB))));

        var row = jdbc.queryForMap("select status, imported_rows from import_batch where id = ?", UUID.fromString(importB));
        assertThat(row.get("status")).isEqualTo("ANALYZED");
        assertThat(row.get("imported_rows")).isEqualTo(0);
    }

    @Test
    @DisplayName("empresa de B nao torna duplicata a linha de A")
    void duplicatesAreScoped() throws Exception {
        mockMvc.perform(post("/imports/" + importB + "/confirm")
                        .session(sessionB).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedRows").value(1));

        String importA = analyze(sessionA);

        mockMvc.perform(get("/imports/" + importA).session(sessionA))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.duplicateRows").value(0))
                // O mesmo arquivo de outra organizacao nao e "reenvio".
                .andExpect(jsonPath("$.previousImportId").doesNotExist());
    }
}
