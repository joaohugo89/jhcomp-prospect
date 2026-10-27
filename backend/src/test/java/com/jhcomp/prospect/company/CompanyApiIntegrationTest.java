package com.jhcomp.prospect.company;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * Empresas pela API (RF-010, RF-012, RF-014, RF-022).
 *
 * <p>Cada teste cria a propria organizacao, de modo que listagens e
 * duplicatas nao dependem do que outras classes gravaram. Dados ficticios
 * (doc 10 §10).
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("API de empresas")
class CompanyApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = TestSessions.login(mockMvc, testData.createActiveOperator("empresas"));
    }

    private ResultActions createCompany(String json) throws Exception {
        return mockMvc.perform(post("/companies")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String createCompanyId(String json) throws Exception {
        String body = createCompany(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String company(String name, String city) {
        return """
                {"name": "%s", "city": "%s", "state": "MG"}
                """.formatted(name, city);
    }

    @Nested
    @DisplayName("cadastro")
    class Create {

        @Test
        @DisplayName("cria empresa com os dados informados e data de criacao")
        void createsCompany() throws Exception {
            createCompany("""
                    {"name": "  Barbearia Ficticia  ", "tradeName": "Ficticia", "segment": "BARBERSHOP",
                     "city": "Cidade Exemplo", "state": "mg", "websiteUrl": "https://www.barbearia.example.test/",
                     "phone": "(35) 98888-7777", "businessEmail": "Contato@Barbearia.example.test"}
                    """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(notNullValue()))
                    .andExpect(jsonPath("$.name").value("Barbearia Ficticia"))
                    .andExpect(jsonPath("$.state").value("MG"))
                    .andExpect(jsonPath("$.businessEmail").value("contato@barbearia.example.test"))
                    .andExpect(jsonPath("$.archived").value(false))
                    .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                    // Valores normalizados servem para comparar, nunca saem na API.
                    .andExpect(jsonPath("$.normalizedName").doesNotExist())
                    .andExpect(jsonPath("$.normalizedPhone").doesNotExist())
                    .andExpect(jsonPath("$.organizationId").doesNotExist());
        }

        @Test
        @DisplayName("registra origem manual padrao quando nenhuma e informada (RF-022)")
        void recordsDefaultSource() throws Exception {
            String id = createCompanyId(company("Padaria Ficticia", "Cidade Exemplo"));

            mockMvc.perform(get("/companies/" + id + "/sources").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].type").value("MANUAL"))
                    .andExpect(jsonPath("$.content[0].name").value("Cadastro manual"))
                    .andExpect(jsonPath("$.content[0].collectedAt").value(notNullValue()))
                    .andExpect(jsonPath("$.content[0].collectedByUserId").value(notNullValue()));
        }

        @Test
        @DisplayName("registra a origem informada pelo operador, sempre como MANUAL (RF-020)")
        void recordsInformedSourceAsManual() throws Exception {
            String id = createCompanyId("""
                    {"name": "Oficina Ficticia", "city": "Cidade Exemplo", "state": "MG",
                     "source": {"name": "Pesquisa no mapa", "url": "https://mapas.example.test/oficina",
                                "collectedAt": "2026-10-01T12:00:00Z", "type": "INTEGRATION"}}
                    """);

            mockMvc.perform(get("/companies/" + id + "/sources").session(session))
                    .andExpect(jsonPath("$.content[0].type").value("MANUAL"))
                    .andExpect(jsonPath("$.content[0].name").value("Pesquisa no mapa"))
                    .andExpect(jsonPath("$.content[0].url").value("https://mapas.example.test/oficina"))
                    .andExpect(jsonPath("$.content[0].collectedAt").value("2026-10-01T12:00:00Z"));
        }

        @Test
        @DisplayName("recusa campos obrigatorios ausentes, com erro por campo")
        void rejectsMissingRequiredFields() throws Exception {
            createCompany("""
                    {"name": "  ", "state": "Minas"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "city", "state")));
        }

        /** A URL vira link na interface; um esquema arbitrario executaria script no clique. */
        @Test
        @DisplayName("recusa URL com esquema que nao seja http ou https")
        void rejectsUnsafeUrlScheme() throws Exception {
            for (String url : new String[] {"javascript:alert(1)", "JavaScript:alert(1)", "data:text/html,x"}) {
                createCompany("""
                        {"name": "Loja Ficticia", "city": "Cidade Exemplo", "state": "MG", "websiteUrl": "%s"}
                        """.formatted(url))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors[0].field").value("websiteUrl"));
            }
        }

        @Test
        @DisplayName("aceita site sem esquema e com porta")
        void acceptsUrlWithoutScheme() throws Exception {
            createCompany("""
                    {"name": "Loja Ficticia", "city": "Cidade Exemplo", "state": "MG",
                     "websiteUrl": "loja.example.test:8080/inicio"}
                    """)
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("recusa origem com data de coleta no futuro")
        void rejectsFutureCollectionDate() throws Exception {
            createCompany("""
                    {"name": "Loja Ficticia", "city": "Cidade Exemplo", "state": "MG",
                     "source": {"name": "Pesquisa", "collectedAt": "2999-01-01T00:00:00Z"}}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("source.collectedAt"));
        }
    }

    @Nested
    @DisplayName("listagem")
    class Listing {

        @Test
        @DisplayName("busca ignora acento, caixa e espaco excedente")
        void searchIsAccentInsensitive() throws Exception {
            createCompanyId(company("Salão Beleza & Estética", "Cidade Exemplo"));
            createCompanyId(company("Mercado Ficticio", "Cidade Exemplo"));

            mockMvc.perform(get("/companies").param("search", "  SALAO   beleza ").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Salão Beleza & Estética"));
        }

        @Test
        @DisplayName("curinga do LIKE e tratado como texto")
        void likeWildcardsAreLiteral() throws Exception {
            createCompanyId(company("Mercado Ficticio", "Cidade Exemplo"));

            mockMvc.perform(get("/companies").param("search", "%").session(session))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("filtros sao combinaveis")
        void filtersCombine() throws Exception {
            createCompany("""
                    {"name": "Barbearia Um", "segment": "BARBERSHOP", "city": "Cidade Exemplo", "state": "MG"}
                    """);
            createCompany("""
                    {"name": "Barbearia Dois", "segment": "BARBERSHOP", "city": "Outra Cidade", "state": "MG"}
                    """);
            createCompany("""
                    {"name": "Padaria Tres", "segment": "BAKERY", "city": "Cidade Exemplo", "state": "MG"}
                    """);

            mockMvc.perform(get("/companies")
                            .param("segment", "BARBERSHOP")
                            .param("city", "cidade exemplo")
                            .session(session))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Barbearia Um"));
        }

        @Test
        @DisplayName("pagina tem tamanho maximo e ordenacao permitida")
        void paginationAndSort() throws Exception {
            createCompanyId(company("Bravo", "Cidade Exemplo"));
            createCompanyId(company("Alfa", "Cidade Exemplo"));

            mockMvc.perform(get("/companies").param("size", "1000").param("sort", "name,asc").session(session))
                    .andExpect(jsonPath("$.size").value(100))
                    .andExpect(jsonPath("$.content[0].name").value("Alfa"));
        }

        @Test
        @DisplayName("recusa ordenacao por campo nao permitido")
        void rejectsUnknownSort() throws Exception {
            mockMvc.perform(get("/companies").param("sort", "normalizedPhone").session(session))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("atualizacao e arquivamento")
    class UpdateAndArchive {

        @Test
        @DisplayName("campo ausente mantem o valor, texto vazio limpa o opcional")
        void partialUpdate() throws Exception {
            String id = createCompanyId("""
                    {"name": "Loja Ficticia", "tradeName": "Loja", "city": "Cidade Exemplo", "state": "MG",
                     "phone": "35 3333-4444"}
                    """);

            mockMvc.perform(patch("/companies/" + id)
                            .session(session)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tradeName": "", "segment": "RETAIL"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Loja Ficticia"))
                    .andExpect(jsonPath("$.phone").value("35 3333-4444"))
                    .andExpect(jsonPath("$.segment").value("RETAIL"))
                    .andExpect(jsonPath("$.tradeName").doesNotExist());
        }

        @Test
        @DisplayName("nao permite limpar campo obrigatorio")
        void cannotClearRequiredField() throws Exception {
            String id = createCompanyId(company("Loja Ficticia", "Cidade Exemplo"));

            mockMvc.perform(patch("/companies/" + id)
                            .session(session)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "   "}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        @DisplayName("arquivada sai da listagem padrao e nao aceita alteracao")
        void archivedCompany() throws Exception {
            String id = createCompanyId(company("Loja Arquivada", "Cidade Exemplo"));

            mockMvc.perform(post("/companies/" + id + "/archive").session(session).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.archived").value(true))
                    .andExpect(jsonPath("$.archivedAt").value(notNullValue()));

            mockMvc.perform(get("/companies").session(session))
                    .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
            mockMvc.perform(get("/companies").param("includeArchived", "true").session(session))
                    .andExpect(jsonPath("$.content[*].id", hasItem(id)));

            mockMvc.perform(patch("/companies/" + id)
                            .session(session)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"segment": "RETAIL"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("CONFLICT"));
        }
    }

    /** Casos do doc 10 §4. */
    @Nested
    @DisplayName("possiveis duplicatas")
    class Duplicates {

        @Test
        @DisplayName("mesmo dominio, com e sem www e esquema")
        void sameDomain() throws Exception {
            String first = createCompanyId("""
                    {"name": "Clinica Um", "city": "Cidade Exemplo", "state": "MG",
                     "websiteUrl": "https://www.clinica.example.test/contato"}
                    """);
            String second = createCompanyId("""
                    {"name": "Clinica Dois", "city": "Outra Cidade", "state": "SP",
                     "websiteUrl": "clinica.example.test"}
                    """);

            mockMvc.perform(get("/companies/" + second + "/duplicates").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].companyId").value(first))
                    .andExpect(jsonPath("$[0].signals[0]").value("DOMAIN"));
        }

        @Test
        @DisplayName("telefone com formatacoes diferentes")
        void samePhoneDifferentFormatting() throws Exception {
            String first = createCompanyId("""
                    {"name": "Pet Um", "city": "Cidade Exemplo", "state": "MG", "phone": "(35) 98888-7777"}
                    """);
            String second = createCompanyId("""
                    {"name": "Pet Dois", "city": "Cidade Exemplo", "state": "MG", "phone": "+55 35 988887777"}
                    """);

            mockMvc.perform(get("/companies/" + second + "/duplicates").session(session))
                    .andExpect(jsonPath("$[0].companyId").value(first))
                    .andExpect(jsonPath("$[0].signals[0]").value("PHONE"));
        }

        @Test
        @DisplayName("nome com acento e espaco na mesma cidade")
        void sameNormalizedNameSameCity() throws Exception {
            String first = createCompanyId(company("Açaí  do Centro", "Cidade Exemplo"));
            String second = createCompanyId(company("acai do centro", "CIDADE EXEMPLO"));

            mockMvc.perform(get("/companies/" + second + "/duplicates").session(session))
                    .andExpect(jsonPath("$[0].companyId").value(first))
                    .andExpect(jsonPath("$[0].signals[0]").value("NAME_CITY_STATE"));
        }

        @Test
        @DisplayName("homonimas em cidades diferentes nao sao sinalizadas")
        void homonymsInDifferentCities() throws Exception {
            createCompanyId(company("Padaria Central", "Cidade Exemplo"));
            String second = createCompanyId(company("Padaria Central", "Outra Cidade"));

            mockMvc.perform(get("/companies/" + second + "/duplicates").session(session))
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("nome parecido nao basta")
        void similarNameIsNotASignal() throws Exception {
            createCompanyId(company("Padaria Central", "Cidade Exemplo"));
            String second = createCompanyId(company("Padaria Central Ltda", "Cidade Exemplo"));

            mockMvc.perform(get("/companies/" + second + "/duplicates").session(session))
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("registro sem site nem telefone so compara nome, cidade e estado")
        void withoutWebsiteOrPhone() throws Exception {
            createCompanyId("""
                    {"name": "Outra Empresa", "city": "Cidade Exemplo", "state": "MG"}
                    """);
            String second = createCompanyId(company("Empresa Sem Contato", "Cidade Exemplo"));

            mockMvc.perform(get("/companies/" + second + "/duplicates").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("varios sinais para a mesma empresa aparecem juntos")
        void multipleSignals() throws Exception {
            String first = createCompanyId("""
                    {"name": "Estudio Foto", "city": "Cidade Exemplo", "state": "MG",
                     "websiteUrl": "estudio.example.test", "phone": "35988887777"}
                    """);
            String second = createCompanyId("""
                    {"name": "Estúdio Foto", "city": "Cidade Exemplo", "state": "MG",
                     "websiteUrl": "https://estudio.example.test", "phone": "(35) 98888-7777"}
                    """);

            mockMvc.perform(get("/companies/" + second + "/duplicates").session(session))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].companyId").value(first))
                    .andExpect(jsonPath("$[0].signals", containsInAnyOrder("DOMAIN", "PHONE", "NAME_CITY_STATE")));
        }
    }
}
