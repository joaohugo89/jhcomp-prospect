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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * The API's single error format (RNF-003, doc 05 §1).
 *
 * <p>It guarantees that errors born in Spring MVC — unknown route,
 * unsupported method, malformed body — come out in the same format as the
 * application's own, and that none of them leaks internal detail.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Error contract")
class ErrorContractTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    /**
     * An authenticated session. Needed in order to reach the 404: unknown
     * routes answer 401 before 404 for anyone not authenticated, which is
     * intentional — it does not reveal to anonymous callers which routes
     * exist.
     */
    private MockHttpSession authenticatedSession(String prefix) throws Exception {
        return TestSessions.login(mockMvc, testData.createActiveAdmin(prefix));
    }

    @Test
    @DisplayName("an unknown route answers 401 for an anonymous caller, without revealing whether it exists")
    void unknownRouteIsUnauthorizedForAnonymous() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("an unknown route answers in the standard format without leaking internal detail")
    void unknownRouteUsesStandardFormat() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe").session(authenticatedSession("err-404")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists())
                // Spring's default handler answered "No static resource ...",
                // which exposes how the request was resolved internally.
                .andExpect(content().string(Matchers.not(Matchers.containsString("static resource"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("org.springframework"))));
    }

    @Test
    @DisplayName("no error response contains a stack trace")
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
    @DisplayName("an unsupported method answers 405 in the standard format")
    void unsupportedMethodUsesStandardFormat() throws Exception {
        mockMvc.perform(put("/health").with(csrf()).session(authenticatedSession("err-405")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    @DisplayName("a malformed JSON body answers 400 in the standard format")
    void malformedJsonUsesStandardFormat() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                // It must reveal neither the parse error's position nor the parser's class.
                .andExpect(content().string(Matchers.not(Matchers.containsString("JsonParse"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("line:"))));
    }

    @Test
    @DisplayName("every response carries the correlation id")
    void everyResponseCarriesRequestId() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));

        mockMvc.perform(get("/rota-que-nao-existe"))
                .andExpect(header().exists("X-Request-Id"));
        // Present in the authenticated error response too.
        mockMvc.perform(get("/rota-que-nao-existe").session(authenticatedSession("err-reqid")))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("health stays public after security was introduced")
    void healthRemainsPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
