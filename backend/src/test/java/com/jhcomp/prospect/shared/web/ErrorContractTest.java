package com.jhcomp.prospect.shared.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;

/**
 * Formato unico de erro da API (RNF-003, doc 05 §1).
 *
 * <p>Garante que erros nascidos no Spring MVC — rota inexistente, metodo nao
 * suportado, corpo malformado — saiam no mesmo formato dos erros da aplicacao,
 * e que nenhum deles vaze detalhe interno.
 */
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(IdentityTestData.class)
@DisplayName("Contrato de erro")
class ErrorContractTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    /**
     * Sessao autenticada. Necessaria para alcancar o 404: rotas desconhecidas
     * respondem 401 antes de 404 para quem nao esta autenticado, o que e
     * intencional — nao revela a anonimos quais rotas existem.
     */
    private org.springframework.mock.web.MockHttpSession authenticatedSession(String prefix) throws Exception {
        var admin = testData.createActiveAdmin(prefix);
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(admin.email(), admin.password());

        return (org.springframework.mock.web.MockHttpSession) mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession();
    }

    @Test
    @DisplayName("rota inexistente responde 401 para anonimo, sem revelar se a rota existe")
    void unknownRouteIsUnauthorizedForAnonymous() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("rota inexistente responde no formato padrao sem vazar detalhe interno")
    void unknownRouteUsesStandardFormat() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe").session(authenticatedSession("err-404")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                // O handler padrao do Spring respondia "No static resource ...",
                // que expoe como a requisicao foi resolvida internamente.
                .andExpect(content().string(Matchers.not(Matchers.containsString("static resource"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("org.springframework"))));
    }

    @Test
    @DisplayName("nenhuma resposta de erro contem stack trace")
    void errorsNeverContainStackTrace() throws Exception {
        String body = mockMvc.perform(get("/rota-que-nao-existe").session(authenticatedSession("err-trace")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("at com.jhcomp")
                .doesNotContain("Exception")
                .doesNotContain("trace");
    }

    @Test
    @DisplayName("metodo nao suportado responde 405 no formato padrao")
    void unsupportedMethodUsesStandardFormat() throws Exception {
        mockMvc.perform(put("/health").with(csrf()).session(authenticatedSession("err-405")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    @DisplayName("corpo JSON malformado responde 400 no formato padrao")
    void malformedJsonUsesStandardFormat() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                // Nao deve revelar a posicao do erro de parsing nem a classe do parser.
                .andExpect(content().string(Matchers.not(Matchers.containsString("JsonParse"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("line:"))));
    }

    @Test
    @DisplayName("toda resposta carrega o correlation id")
    void everyResponseCarriesRequestId() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));

        mockMvc.perform(get("/rota-que-nao-existe"))
                .andExpect(header().exists("X-Request-Id"));
        // Tambem presente na resposta de erro autenticada.
        mockMvc.perform(get("/rota-que-nao-existe").session(authenticatedSession("err-reqid")))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("health permanece publico apos a introducao da seguranca")
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
