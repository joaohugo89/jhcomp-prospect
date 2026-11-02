package com.jhcomp.prospect.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** Segmento com uma grafia so por organizacao (D31): ao gravar e na migration V13. Dados ficticios. */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Segmento com grafia unica")
class SegmentCanonicalIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private IdentityTestData.Member admin;
    private MockHttpSession session;

    SegmentCanonicalIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = testData.createActiveAdmin("segmento");
        session = TestSessions.login(mockMvc, admin);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String create(String name, String segment) throws Exception {
        return JsonPath.read(send(post("/companies"), """
                {"name": "%s", "segment": "%s", "city": "Cidade Exemplo", "state": "MG"}
                """.formatted(name, segment)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(), "$.segment");
    }

    @Test
    @DisplayName("ao gravar, adota a grafia que ja existe e tira espaco repetido")
    void onWrite() throws Exception {
        assertThat(create("Barbearia Um", "Barbearia")).isEqualTo("Barbearia");
        assertThat(create("Barbearia Dois", "  barbearia ")).isEqualTo("Barbearia");
        assertThat(create("Padaria Um", "Padaria   do   Centro")).isEqualTo("Padaria do Centro");
        // Tabulacao tambem e espaco em branco: a regex precisa ser \\s, nao um espaco literal.
        assertThat(create("Padaria Dois", "padaria\\tdo centro")).isEqualTo("Padaria do Centro");

        String id = JsonPath.read(send(post("/companies"),
                "{\"name\": \"Loja Edicao\", \"city\": \"Cidade Exemplo\", \"state\": \"MG\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        send(patch("/companies/" + id), "{\"segment\": \"BARBEARIA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segment").value("Barbearia"));
        // Segmento novo fica como foi digitado.
        assertThat(create("Floricultura Um", "Floricultura")).isEqualTo("Floricultura");
    }

    @Test
    @DisplayName("migration V13 unifica as grafias existentes pela mais usada, sem mexer em outra organizacao")
    void migrationUnifiesExisting() throws IOException {
        IdentityTestData.Member other = testData.createActiveAdmin("segmento-b");
        insert(admin.organizationId(), "Barbearia");
        insert(admin.organizationId(), "Barbearia");
        insert(admin.organizationId(), "barbearia");
        insert(admin.organizationId(), " BARBEARIA  ");
        insert(admin.organizationId(), "   ");
        insert(other.organizationId(), "barbearia");

        for (String statement : segmentStatements()) {
            jdbc.execute(statement);
        }

        List<String> mine = jdbc.queryForList(
                "select coalesce(segment, '<nulo>') from company where organization_id = ? order by 1",
                String.class, admin.organizationId());
        assertThat(mine).containsExactly("<nulo>", "Barbearia", "Barbearia", "Barbearia", "Barbearia");
        // Outra organizacao tem a propria grafia mais usada.
        assertThat(jdbc.queryForObject("select segment from company where organization_id = ?", String.class,
                other.organizationId())).isEqualTo("barbearia");
    }

    /** Os comandos de segmento da V13 (os que mexem em company), executados sobre dados de teste. */
    private static List<String> segmentStatements() throws IOException {
        String sql = new String(new ClassPathResource("db/migration/V13__segment_canonical_and_shared_views.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String withoutComments = sql.lines()
                .filter(line -> !line.strip().startsWith("--"))
                .reduce("", (all, line) -> all + line + "\n");
        return Arrays.stream(withoutComments.split(";"))
                .map(String::strip)
                .filter(statement -> statement.startsWith("UPDATE company") || statement.startsWith("WITH spellings"))
                .toList();
    }

    private void insert(UUID organizationId, String segment) {
        String name = "Empresa " + UUID.randomUUID();
        jdbc.update("""
                insert into company (id, organization_id, name, normalized_name, segment, city, state)
                values (?, ?, ?, ?, ?, 'Cidade Exemplo', 'MG')
                """, UUID.randomUUID(), organizationId, name, name.toLowerCase(), segment);
    }
}
