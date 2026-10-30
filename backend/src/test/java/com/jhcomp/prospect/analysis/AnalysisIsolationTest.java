package com.jhcomp.prospect.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.analysis.application.WebsiteChecker;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;
import com.jhcomp.prospect.shared.jobs.JobWorker;

/**
 * Analises mantem o escopo da organizacao (doc 10 §3, D8).
 *
 * <p>Inclui a rota sem lead no caminho, {@code /findings/{id}/review}, que a D8
 * aponta como o ponto em que a cadeia de posse precisa ser verificada.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Isolamento entre organizacoes: analise")
class AnalysisIsolationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    @Autowired
    private JobWorker jobWorker;

    @MockitoBean
    private WebsiteChecker websiteChecker;

    private final JdbcTemplate jdbc;

    private MockHttpSession sessionA;
    private String leadB;
    private String analysisB;
    private String findingB;

    AnalysisIsolationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        when(websiteChecker.inspect(any())).thenReturn(WebsiteObservation.noWebsite(Instant.now()));
        sessionA = TestSessions.login(mockMvc, testData.createActiveAdmin("an-a"));
        MockHttpSession sessionB = TestSessions.login(mockMvc, testData.createActiveAdmin("an-b"));

        String companyB = JsonPath.read(mockMvc.perform(post("/companies").session(sessionB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Empresa de B\", \"city\": \"Cidade Exemplo\", \"state\": \"MG\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        leadB = JsonPath.read(mockMvc.perform(post("/leads").session(sessionB).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"companyId\": \"%s\"}".formatted(companyB)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        analysisB = JsonPath.read(mockMvc.perform(post("/leads/" + leadB + "/analyses").session(sessionB).with(csrf()))
                .andReturn().getResponse().getContentAsString(), "$.id");
        jobWorker.runPending();
        findingB = jdbc.queryForObject(
                "select id::text from analysis_finding where analysis_id = ? and criterion = 'SITE_ABSENT'",
                String.class, UUID.fromString(analysisB));
    }

    private void expectNotFound(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.session(sessionA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CONFIRMED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("usuario de A nao pede, nao lista, nao le e nao revisa analise de B")
    void allRoutesAreScoped() throws Exception {
        expectNotFound(post("/leads/" + leadB + "/analyses"));
        expectNotFound(get("/leads/" + leadB + "/analyses"));
        expectNotFound(get("/analyses/" + analysisB));
        expectNotFound(patch("/findings/" + findingB + "/review"));

        Integer analyses = jdbc.queryForObject(
                "select count(*) from lead_analysis where lead_id = ?", Integer.class, UUID.fromString(leadB));
        assertThat(analyses).isEqualTo(1);
        var finding = jdbc.queryForMap(
                "select finding_status, review_status from analysis_finding where id = ?", UUID.fromString(findingB));
        assertThat(finding.get("finding_status")).isEqualTo("NOT_VERIFIED");
        assertThat(finding.get("review_status")).isEqualTo("PENDING");
    }
}
