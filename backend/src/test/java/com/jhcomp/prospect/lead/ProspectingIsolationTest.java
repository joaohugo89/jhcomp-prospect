package com.jhcomp.prospect.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * Isolamento entre organizacoes em empresas e leads (doc 10 §3).
 *
 * <p>Usuario de A conhece os UUIDs de B e tenta todas as rotas com eles. Toda
 * tentativa responde 404, e nao 403, para nao confirmar que o recurso existe; e
 * o banco confirma que nada de B mudou.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Isolamento entre organizacoes: empresas e leads")
class ProspectingIsolationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private IdentityTestData.Member memberB;
    private MockHttpSession sessionA;
    private MockHttpSession sessionB;
    private String companyB;
    private String leadB;

    ProspectingIsolationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        sessionA = TestSessions.login(mockMvc, testData.createActiveAdmin("iso-a"));
        memberB = testData.createActiveAdmin("iso-b");
        sessionB = TestSessions.login(mockMvc, memberB);

        companyB = JsonPath.read(send(post("/companies"), sessionB, """
                {"name": "Empresa de B", "city": "Cidade Exemplo", "state": "MG",
                 "websiteUrl": "empresa-b.example.test", "phone": "35 3333-4444"}
                """), "$.id");
        leadB = JsonPath.read(send(post("/leads"), sessionB, """
                {"companyId": "%s"}
                """.formatted(companyB)), "$.id");
    }

    private String send(MockHttpServletRequestBuilder request, MockHttpSession session, String body) throws Exception {
        return mockMvc.perform(request
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void expectNotFound(MockHttpServletRequestBuilder request, String body) throws Exception {
        mockMvc.perform(request
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("usuario de A nao lista empresas nem leads de B")
    void listingsAreScoped() throws Exception {
        mockMvc.perform(get("/companies").param("includeArchived", "true").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(companyB))));
        mockMvc.perform(get("/companies").param("search", "Empresa de B").session(sessionA))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/leads").session(sessionA))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(leadB))));
    }

    @Test
    @DisplayName("usuario de A nao le empresa de B por UUID, em nenhuma rota")
    void companyReadsAreScoped() throws Exception {
        expectNotFound(get("/companies/" + companyB), "");
        expectNotFound(get("/companies/" + companyB + "/sources"), "");
        expectNotFound(get("/companies/" + companyB + "/duplicates"), "");
    }

    @Test
    @DisplayName("usuario de A nao altera nem arquiva empresa de B")
    void companyWritesAreScoped() throws Exception {
        expectNotFound(patch("/companies/" + companyB), "{\"name\": \"Alterado por A\"}");
        expectNotFound(post("/companies/" + companyB + "/archive"), "");

        var row = jdbc.queryForMap("select name, archived_at from company where id = ?", UUID.fromString(companyB));
        assertThat(row.get("name")).isEqualTo("Empresa de B");
        assertThat(row.get("archived_at")).isNull();
    }

    @Test
    @DisplayName("usuario de A nao abre lead para empresa de B")
    void cannotCreateLeadForOtherOrganizationCompany() throws Exception {
        // Encerra o lead de B para que so o isolamento possa impedir a criacao.
        send(patch("/leads/" + leadB + "/status"), sessionB, "{\"status\": \"LOST\"}");

        expectNotFound(post("/leads"), "{\"companyId\": \"%s\"}".formatted(companyB));

        Integer leads = jdbc.queryForObject(
                "select count(*) from lead where company_id = ?", Integer.class, UUID.fromString(companyB));
        assertThat(leads).isEqualTo(1);
    }

    @Test
    @DisplayName("usuario de A nao le nem altera lead de B por UUID")
    void leadRoutesAreScoped() throws Exception {
        expectNotFound(get("/leads/" + leadB), "");
        expectNotFound(get("/leads/" + leadB + "/timeline"), "");
        expectNotFound(patch("/leads/" + leadB + "/status"), "{\"status\": \"QUALIFIED\"}");
        expectNotFound(patch("/leads/" + leadB + "/priority"), "{\"priority\": \"HIGH\"}");
        expectNotFound(patch("/leads/" + leadB), "{\"assignedUserId\": null}");

        var row = jdbc.queryForMap("select status, priority from lead where id = ?", UUID.fromString(leadB));
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("priority")).isEqualTo("MEDIUM");
        Integer changes = jdbc.queryForObject(
                "select count(*) from lead_status_change where lead_id = ?", Integer.class, UUID.fromString(leadB));
        assertThat(changes).isEqualTo(1);
    }

    @Test
    @DisplayName("usuario de A nao atribui lead proprio a membro de B")
    void cannotAssignToOtherOrganizationMember() throws Exception {
        String companyA = JsonPath.read(send(post("/companies"), sessionA, """
                {"name": "Empresa de A", "city": "Cidade Exemplo", "state": "MG"}
                """), "$.id");
        String leadA = JsonPath.read(send(post("/leads"), sessionA, """
                {"companyId": "%s"}
                """.formatted(companyA)), "$.id");

        expectNotFound(patch("/leads/" + leadA), "{\"assignedUserId\": \"%s\"}".formatted(memberB.userId()));
    }

    @Test
    @DisplayName("duplicatas nunca sugerem empresa de outra organizacao")
    void duplicatesAreScoped() throws Exception {
        // Mesmo dominio e telefone da empresa de B: seria duplicata forte se
        // estivesse na mesma organizacao.
        String companyA = JsonPath.read(send(post("/companies"), sessionA, """
                {"name": "Empresa de B", "city": "Cidade Exemplo", "state": "MG",
                 "websiteUrl": "https://empresa-b.example.test", "phone": "(35) 3333-4444"}
                """), "$.id");

        mockMvc.perform(get("/companies/" + companyA + "/duplicates").session(sessionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
