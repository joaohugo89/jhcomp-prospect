package com.jhcomp.prospect.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.analysis.application.WebsiteChecker;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation.Outcome;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;
import com.jhcomp.prospect.shared.jobs.JobWorker;

/**
 * Analise de presenca digital pela API (RF-030 a RF-033, D3, D15).
 *
 * <p>O site nunca e acessado de verdade: {@link WebsiteChecker} e substituido
 * por um dublê. O worker roda quando o teste chama, sem depender de tempo.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("API de analise")
class AnalysisApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    @Autowired
    private JobWorker jobWorker;

    @MockitoBean
    private WebsiteChecker websiteChecker;

    private final JdbcTemplate jdbc;

    private MockHttpSession session;

    AnalysisApiIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** Site so HTTP, sem viewport e sem link de contato. */
    private static WebsiteObservation siteWithProblems() {
        return new WebsiteObservation(Outcome.PAGE_READ, "https://loja.example.test", "http://loja.example.test/",
                false, 200, false, false, "b".repeat(64), null, Instant.parse("2026-11-05T12:00:00Z"));
    }

    @BeforeEach
    void setUp() throws Exception {
        session = TestSessions.login(mockMvc, testData.createActiveOperator("analise"));
        when(websiteChecker.inspect(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == null ? WebsiteObservation.noWebsite(Instant.now()) : siteWithProblems());
    }

    private String json(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private String createLead(String companyJson) throws Exception {
        String companyId = JsonPath.read(json(mockMvc.perform(post("/companies")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(companyJson))
                .andExpect(status().isCreated())), "$.id");
        return JsonPath.read(json(mockMvc.perform(post("/leads")
                        .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyId\": \"%s\"}".formatted(companyId)))
                .andExpect(status().isCreated())), "$.id");
    }

    private String leadWithSite() throws Exception {
        return createLead("""
                {"name": "Loja Ficticia", "segment": "RETAIL", "city": "Cidade Exemplo", "state": "MG",
                 "websiteUrl": "loja.example.test"}
                """);
    }

    private ResultActions requestAnalysis(String leadId) throws Exception {
        return mockMvc.perform(post("/leads/" + leadId + "/analyses").session(session).with(csrf()));
    }

    private String completedAnalysis(String leadId) throws Exception {
        String id = JsonPath.read(json(requestAnalysis(leadId).andExpect(status().isAccepted())), "$.id");
        jobWorker.runPending();
        return id;
    }

    private String findingId(String analysisId, String criterion) throws Exception {
        String body = json(mockMvc.perform(get("/analyses/" + analysisId).session(session)));
        return JsonPath.<java.util.List<String>>read(body, "$.findings[?(@.criterion == '" + criterion + "')].id").getFirst();
    }

    private ResultActions review(String findingId, String body) throws Exception {
        return mockMvc.perform(patch("/findings/" + findingId + "/review")
                .session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("pedido responde 202 e a analise roda em segundo plano")
    void runsInBackground() throws Exception {
        String leadId = leadWithSite();

        String analysisId = JsonPath.read(json(requestAnalysis(leadId)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.score").doesNotExist())), "$.id");

        assertThat(jobWorker.runPending()).isEqualTo(1);

        // HTTPS 10 + viewport 10 + contato 10 + informacoes 15 + localizacao 10.
        // Segmentos atendidos nao configurados: compatibilidade fica para revisao.
        mockMvc.perform(get("/analyses/" + analysisId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.status").value("COMPLETED"))
                .andExpect(jsonPath("$.analysis.score").value(55))
                .andExpect(jsonPath("$.analysis.scoreVersion").value("v1"))
                .andExpect(jsonPath("$.findings.length()").value(8))
                .andExpect(jsonPath("$.findings[0].criterion").value("SITE_ABSENT"))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'NO_HTTPS')].pointsAwarded").value(10))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'NO_HTTPS')].evidence[0].type").value("HTTP_RESPONSE"))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'SERVICE_FIT')].status").value("NOT_VERIFIED"))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'SERVICE_FIT')].pointsAwarded").value(0));

        // D3: o lead recebe o score da ultima analise concluida.
        mockMvc.perform(get("/leads/" + leadId).session(session))
                .andExpect(jsonPath("$.score").value(55));
    }

    /** Regra 3: nao encontrado nao e deficiencia. */
    @Test
    @DisplayName("empresa sem site cadastrado nao ganha os 25 pontos de ausencia sem revisao")
    void noWebsiteIsNotPenalized() throws Exception {
        String leadId = createLead("""
                {"name": "Sem Site", "segment": "RETAIL", "city": "Cidade Exemplo", "state": "MG", "phone": "35 3333-0000"}
                """);
        String analysisId = completedAnalysis(leadId);

        mockMvc.perform(get("/analyses/" + analysisId).session(session))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'SITE_ABSENT')].status").value("NOT_VERIFIED"))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'SITE_ABSENT')].pointsAwarded").value(0))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'NO_HTTPS')].status").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.analysis.score").value(25));
    }

    @Test
    @DisplayName("revisao corrige o achado, preserva o original, vira evidencia e recalcula o score")
    void reviewRescores() throws Exception {
        String leadId = leadWithSite();
        String analysisId = completedAnalysis(leadId);
        String booking = findingId(analysisId, "NO_ONLINE_BOOKING_OR_CATALOG");

        review(booking, "{\"status\": \"CONFIRMED\", \"note\": \"Agenda so por telefone, confirmado com a empresa.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.score").value(65))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'NO_ONLINE_BOOKING_OR_CATALOG')].reviewStatus").value("CORRECTED"))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'NO_ONLINE_BOOKING_OR_CATALOG')].originalStatus").value("NOT_VERIFIED"))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'NO_ONLINE_BOOKING_OR_CATALOG')].reviewedAt").exists())
                .andExpect(jsonPath("$.findings[?(@.criterion == 'NO_ONLINE_BOOKING_OR_CATALOG')].evidence[*].type",
                        hasItem("OPERATOR_NOTE")));

        mockMvc.perform(get("/leads/" + leadId).session(session)).andExpect(jsonPath("$.score").value(65));

        // Mesmo estado de novo: conferido, sem mudar pontos.
        review(booking, "{\"status\": \"CONFIRMED\"}")
                .andExpect(jsonPath("$.analysis.score").value(65))
                .andExpect(jsonPath("$.findings[?(@.criterion == 'NO_ONLINE_BOOKING_OR_CATALOG')].reviewStatus").value("CONFIRMED"));
    }

    @Test
    @DisplayName("rejeitar um problema apontado pela verificacao tira os pontos")
    void rejectingRemovesPoints() throws Exception {
        String leadId = leadWithSite();
        String analysisId = completedAnalysis(leadId);

        review(findingId(analysisId, "NO_CONTACT_LINK"), "{\"status\": \"CONTRADICTED\", \"note\": \"Contato no rodape, gerado por script.\"}")
                .andExpect(jsonPath("$.analysis.score").value(45));
    }

    @Test
    @DisplayName("pedido repetido com analise em andamento devolve a mesma analise")
    void idempotentWhileActive() throws Exception {
        String leadId = leadWithSite();

        String first = JsonPath.read(json(requestAnalysis(leadId)), "$.id");
        String second = JsonPath.read(json(requestAnalysis(leadId).andExpect(status().isAccepted())), "$.id");

        assertThat(second).isEqualTo(first);
        assertThat(jobWorker.runPending()).isEqualTo(1);
    }

    @Test
    @DisplayName("lead marcado como nao contatar nao e analisado")
    void doNotContactIsNotAnalyzed() throws Exception {
        String leadId = leadWithSite();
        mockMvc.perform(patch("/leads/" + leadId + "/status").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"DO_NOT_CONTACT\"}"))
                .andExpect(status().isOk());

        requestAnalysis(leadId).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("analise pendente nao aceita revisao")
    void pendingCannotBeReviewed() throws Exception {
        String leadId = leadWithSite();
        String analysisId = JsonPath.read(json(requestAnalysis(leadId)), "$.id");
        // Gera os achados de outra analise, ja concluida, para ter um id valido.
        jobWorker.runPending();
        String finding = findingId(analysisId, "SITE_ABSENT");
        jdbc.update("update lead_analysis set status = 'PROCESSING', score = null, score_version = null, "
                + "completed_at = null where id = ?", UUID.fromString(analysisId));

        review(finding, "{\"status\": \"CONFIRMED\"}").andExpect(status().isConflict());
    }

    /** Falha inesperada: tenta de novo com espera; esgotadas as tentativas, a analise falha. */
    @Test
    @DisplayName("falha repetida esgota as tentativas e marca a analise como falha")
    void retriesThenFails() throws Exception {
        when(websiteChecker.inspect(any())).thenThrow(new IllegalStateException("falha simulada"));
        String leadId = leadWithSite();
        String analysisId = JsonPath.read(json(requestAnalysis(leadId)), "$.id");

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(jobWorker.runPending()).describedAs("tentativa %d", attempt).isEqualTo(1);
            // Pula a espera entre tentativas. Instante bem no passado: o relogio
            // do banco em container pode estar adiantado em relacao ao da JVM.
            jdbc.update("update job set run_after = now() - interval '1 hour' where idempotency_key = ?", analysisId);
        }
        assertThat(jobWorker.runPending()).isZero();

        mockMvc.perform(get("/analyses/" + analysisId).session(session))
                .andExpect(jsonPath("$.analysis.status").value("FAILED"))
                .andExpect(jsonPath("$.analysis.failureReason").exists())
                .andExpect(jsonPath("$.analysis.score").doesNotExist());
        var job = jdbc.queryForMap("select status, attempts, last_error from job where idempotency_key = ?", analysisId);
        assertThat(job.get("status")).isEqualTo("FAILED");
        assertThat(job.get("attempts")).isEqualTo(3);
        // So a classe do erro, sem mensagem interna.
        assertThat(job.get("last_error")).isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("lista as analises do lead, mais recente primeiro")
    void listsAnalyses() throws Exception {
        String leadId = leadWithSite();
        String first = completedAnalysis(leadId);
        String second = completedAnalysis(leadId);

        mockMvc.perform(get("/leads/" + leadId + "/analyses").session(session))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first));
    }
}
