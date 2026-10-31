package com.jhcomp.prospect.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/** Outreach e lista de nao contato (RF-050 a RF-053, doc 10 §6, D26). */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Outreach: rascunho, aprovacao, registro de envio e nao contato")
class OutreachApiIntegrationTest extends PostgresIntegrationTest {

    /** Dados ficticios (doc 10 §10). */
    private static final String PHONE = "(35) 98888-1234";
    private static final String EMAIL = "contato@padaria.example.test";
    private static final String INSTAGRAM = "https://instagram.com/padaria.exemplo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    private final JdbcTemplate jdbc;

    private MockHttpSession session;
    private String leadId;

    OutreachApiIntegrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        session = TestSessions.login(mockMvc, testData.createActiveAdmin("outreach"));
        leadId = createLead(session, PHONE, EMAIL, INSTAGRAM);
    }

    // ---- Helpers -------------------------------------------------------------

    private ResultActions perform(MockHttpServletRequestBuilder request, MockHttpSession as, String json) throws Exception {
        return mockMvc.perform(request.session(as).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String ok(MockHttpServletRequestBuilder request, MockHttpSession as, String json) throws Exception {
        return perform(request, as, json).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
    }

    private String createLead(MockHttpSession as, String phone, String email, String instagram) throws Exception {
        String company = JsonPath.read(ok(post("/companies"), as, """
                {"name": "Padaria Exemplo", "segment": "Padaria", "city": "Cidade Exemplo", "state": "MG",
                 "phone": %s, "businessEmail": %s, "instagramUrl": %s}
                """.formatted(quoted(phone), quoted(email), quoted(instagram))), "$.id");
        return JsonPath.read(ok(post("/leads"), as, "{\"companyId\": \"%s\"}".formatted(company)), "$.id");
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private void moveTo(String lead, String... statuses) throws Exception {
        for (String target : statuses) {
            ok(patch("/leads/" + lead + "/status"), session, "{\"status\": \"%s\", \"contactConfirmed\": true}".formatted(target));
        }
    }

    private ResultActions generate(String lead, String channel) throws Exception {
        return perform(post("/leads/" + lead + "/messages/generate"), session, "{\"channel\": \"%s\"}".formatted(channel));
    }

    private String generated(String channel) throws Exception {
        return JsonPath.read(generate(leadId, channel).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String approved(String channel) throws Exception {
        String message = generated(channel);
        ok(post("/messages/" + message + "/approve"), session, "{\"unverifiedClaimsReviewed\": true}");
        return message;
    }

    private ResultActions recordSend(String message, String result, String extra) throws Exception {
        return perform(post("/messages/" + message + "/record-send"), session,
                "{\"result\": \"%s\", \"sentAt\": \"%s\"%s}".formatted(result, Instant.now(), extra));
    }

    private ResultActions suppress(MockHttpSession as, String type, String identifier, String channel) throws Exception {
        return perform(post("/suppression-entries"), as, """
                {"identifierType": "%s", "identifier": "%s", "channel": %s, "reason": "Pedido por telefone"}
                """.formatted(type, identifier, quoted(channel)));
    }

    private String leadStatus(String lead) {
        return jdbc.queryForObject("select status from lead where id = ?", String.class, UUID.fromString(lead));
    }

    private int interactions(String lead) {
        Integer count = jdbc.queryForObject(
                "select count(*) from interaction where lead_id = ?", Integer.class, UUID.fromString(lead));
        return count == null ? 0 : count;
    }

    // ---- Geracao ---------------------------------------------------------------

    @Test
    @DisplayName("mensagem gerada nasce em revisao, com provedor e versao do prompt, e o lead nao muda")
    void generatedMessageStartsAsDraft() throws Exception {
        generate(leadId, "EMAIL")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.subject").value("Presença digital da Padaria Exemplo"))
                .andExpect(jsonPath("$.body", containsString("Padaria Exemplo")))
                .andExpect(jsonPath("$.unverifiedClaims", hasSize(1)))
                .andExpect(jsonPath("$.generatedBy").value("fake:template-v1"))
                .andExpect(jsonPath("$.promptVersion").value("outreach-v1"))
                .andExpect(jsonPath("$.approvedAt").doesNotExist())
                .andExpect(jsonPath("$.sentAt").doesNotExist());

        assertThat(leadStatus(leadId)).isEqualTo("NEW");
        mockMvc.perform(get("/leads/" + leadId + "/messages").session(session))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("contexto de composicao mostra remetente, destinatarios e bloqueio por canal")
    void composeContext() throws Exception {
        String other = createLead(session, null, EMAIL.replace("contato", "outro"), null);

        mockMvc.perform(get("/leads/" + other + "/outreach-context").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Padaria Exemplo"))
                .andExpect(jsonPath("$.senderName").isNotEmpty())
                .andExpect(jsonPath("$.senderOrganization").isNotEmpty())
                .andExpect(jsonPath("$.confirmedFindings", hasSize(0)))
                .andExpect(jsonPath("$.channels[0].recipient").doesNotExist())
                .andExpect(jsonPath("$.channels[0].channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.channels[1].channel").value("EMAIL"))
                .andExpect(jsonPath("$.channels[1].recipient").value("outro@padaria.example.test"))
                .andExpect(jsonPath("$.channels[1].blocked").value(false));
    }

    @Test
    @DisplayName("canal sem destinatario valido na empresa e recusado")
    void channelWithoutRecipient() throws Exception {
        String lead = createLead(session, null, null, null);

        generate(lead, "EMAIL")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("EMAIL")));
    }

    @Test
    @DisplayName("limite de geracao por usuario responde 429")
    void generationIsRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            generate(leadId, "WHATSAPP").andExpect(status().isCreated());
        }
        generate(leadId, "WHATSAPP")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("TOO_MANY_REQUESTS"));
    }

    // ---- Revisao e aprovacao ---------------------------------------------------

    @Test
    @DisplayName("aprovar afirmacao nao verificada exige confirmacao de revisao")
    void approvalRequiresReviewOfClaims() throws Exception {
        String message = generated("WHATSAPP");

        perform(post("/messages/" + message + "/approve"), session, "{}")
                .andExpect(status().isBadRequest());
        perform(post("/messages/" + message + "/approve"), session, "{\"unverifiedClaimsReviewed\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty())
                // Aprovada nao e enviada (regra 5).
                .andExpect(jsonPath("$.sentAt").doesNotExist());

        assertThat(leadStatus(leadId)).as("aprovar nao move o lead (regra 6)").isEqualTo("NEW");
        assertThat(interactions(leadId)).isZero();
    }

    @Test
    @DisplayName("editar mensagem aprovada devolve a revisao e limpa a aprovacao")
    void editingApprovedMessageResetsApproval() throws Exception {
        String message = approved("EMAIL");

        perform(patch("/messages/" + message), session, "{\"subject\": \"Novo assunto\", \"body\": \"Texto revisado\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.body").value("Texto revisado"))
                .andExpect(jsonPath("$.approvedAt").doesNotExist());
    }

    @Test
    @DisplayName("edicao respeita as regras do canal")
    void editingValidatesChannelRules() throws Exception {
        String message = generated("WHATSAPP");

        perform(patch("/messages/" + message), session, "{\"subject\": \"Assunto\", \"body\": \"Texto\"}")
                .andExpect(status().isBadRequest());
        perform(patch("/messages/" + message), session, "{\"body\": \"%s\"}".formatted("a".repeat(1001)))
                .andExpect(status().isBadRequest());
    }

    // ---- Registro de envio ---------------------------------------------------

    @Test
    @DisplayName("so mensagem aprovada pode ter envio registrado")
    void onlyApprovedCanBeSent() throws Exception {
        String message = generated("WHATSAPP");

        recordSend(message, "SENT", "").andExpect(status().isConflict());
        assertThat(interactions(leadId)).isZero();
    }

    @Test
    @DisplayName("envio confirmado vira interacao, mas o lead so muda se o operador pedir")
    void recordSendCreatesInteraction() throws Exception {
        String message = approved("WHATSAPP");

        recordSend(message, "SENT", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty());

        assertThat(interactions(leadId)).isEqualTo(1);
        assertThat(leadStatus(leadId)).isEqualTo("NEW");
        var interaction = jdbc.queryForMap(
                "select interaction_type, channel, direction from interaction where lead_id = ?", UUID.fromString(leadId));
        assertThat(interaction).containsEntry("interaction_type", "CONTACT").containsEntry("channel", "WHATSAPP")
                .containsEntry("direction", "OUTBOUND");

        recordSend(message, "SENT", "").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("marcar contatado move o lead a CONTACTED junto com o registro")
    void recordSendCanMarkContacted() throws Exception {
        moveTo(leadId, "QUALIFIED", "CONTACT_APPROVED");
        String message = approved("EMAIL");

        recordSend(message, "SENT", ", \"markLeadContacted\": true").andExpect(status().isOk());

        assertThat(leadStatus(leadId)).isEqualTo("CONTACTED");
    }

    @Test
    @DisplayName("envio que falhou nao conta como contato nem marca o lead")
    void failedSend() throws Exception {
        moveTo(leadId, "QUALIFIED", "CONTACT_APPROVED");
        String message = approved("EMAIL");

        recordSend(message, "FAILED", ", \"markLeadContacted\": true").andExpect(status().isBadRequest());
        recordSend(message, "FAILED", "").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(interactions(leadId)).isZero();
        assertThat(leadStatus(leadId)).isEqualTo("CONTACT_APPROVED");
    }

    @Test
    @DisplayName("data de envio no futuro e recusada")
    void rejectsFutureSendDate() throws Exception {
        String message = approved("EMAIL");

        perform(post("/messages/" + message + "/record-send"), session,
                "{\"result\": \"SENT\", \"sentAt\": \"%s\"}".formatted(Instant.now().plus(1, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("envio no mesmo minuto da aprovacao e aceito, como a tela informa; antes dela, nao")
    void sendDateHasMinutePrecision() throws Exception {
        String message = approved("EMAIL");
        Instant approvedAt = Instant.parse(JsonPath.read(
                mockMvc.perform(get("/leads/" + leadId + "/messages").session(session))
                        .andReturn().getResponse().getContentAsString(),
                "$.content[0].approvedAt"));

        perform(post("/messages/" + message + "/record-send"), session,
                "{\"result\": \"SENT\", \"sentAt\": \"%s\"}".formatted(approvedAt.minus(1, ChronoUnit.HOURS)))
                .andExpect(status().isBadRequest());
        perform(post("/messages/" + message + "/record-send"), session,
                "{\"result\": \"SENT\", \"sentAt\": \"%s\"}".formatted(approvedAt.truncatedTo(ChronoUnit.MINUTES)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("cancelar encerra a mensagem; mensagem encerrada nao muda mais")
    void cancel() throws Exception {
        String message = generated("EMAIL");

        perform(post("/messages/" + message + "/cancel"), session, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        perform(post("/messages/" + message + "/cancel"), session, "").andExpect(status().isConflict());
        perform(patch("/messages/" + message), session, "{\"body\": \"x\"}").andExpect(status().isConflict());
        perform(post("/messages/" + message + "/approve"), session, "{\"unverifiedClaimsReviewed\": true}")
                .andExpect(status().isConflict());
    }

    // ---- Nao contato -----------------------------------------------------------

    @Test
    @DisplayName("telefone bloqueado em outro formato impede gerar em qualquer canal")
    void suppressedPhoneBlocksGeneration() throws Exception {
        suppress(session, "PHONE", "+55 35 98888 1234", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.identifier").value("+5535988881234"));

        generate(leadId, "WHATSAPP").andExpect(status().isConflict());
        generate(leadId, "PHONE_CALL").andExpect(status().isConflict());
        // Conservador: um identificador da empresa bloqueado em todos os canais bloqueia a empresa.
        generate(leadId, "EMAIL").andExpect(status().isConflict());
        mockMvc.perform(get("/leads/" + leadId + "/outreach-context").session(session))
                .andExpect(jsonPath("$.channels[1].blocked").value(true));
    }

    @Test
    @DisplayName("bloqueio restrito a um canal libera os demais")
    void channelScopedSuppression() throws Exception {
        suppress(session, "INSTAGRAM", "@Padaria.Exemplo", "INSTAGRAM").andExpect(status().isCreated());

        generate(leadId, "INSTAGRAM").andExpect(status().isConflict());
        generate(leadId, "EMAIL").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("bloqueio registrado depois da aprovacao impede registrar envio")
    void suppressionAfterApprovalBlocksSend() throws Exception {
        String message = approved("EMAIL");
        suppress(session, "EMAIL", EMAIL.toUpperCase(), null).andExpect(status().isCreated());

        recordSend(message, "SENT", "").andExpect(status().isConflict());
        String messageStatus = jdbc.queryForObject(
                "select status from outreach_message where id = ?", String.class, UUID.fromString(message));
        assertThat(messageStatus).isEqualTo("APPROVED");
        assertThat(interactions(leadId)).isZero();
    }

    @Test
    @DisplayName("bloqueio registrado depois do rascunho impede aprovar")
    void suppressionBlocksApproval() throws Exception {
        String message = generated("WHATSAPP");
        suppress(session, "PHONE", PHONE, "WHATSAPP").andExpect(status().isCreated());

        perform(post("/messages/" + message + "/approve"), session, "{\"unverifiedClaimsReviewed\": true}")
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("pedido de nao contato no lead bloqueia a empresa, inclusive recadastrada com o mesmo contato")
    void doNotContactRequestSuppressesIdentifiers() throws Exception {
        moveTo(leadId, "QUALIFIED", "CONTACT_APPROVED", "CONTACTED", "IN_CONVERSATION", "WON");
        ok(post("/leads/" + leadId + "/interactions"), session, """
                {"type": "DO_NOT_CONTACT_REQUEST", "channel": "PHONE", "direction": "INBOUND", "occurredAt": "%s"}
                """.formatted(Instant.now()));

        Integer entries = jdbc.queryForObject("""
                select count(*) from suppression_entry e
                join lead l on l.organization_id = e.organization_id
                where l.id = ? and e.channel is null
                """, Integer.class, UUID.fromString(leadId));
        assertThat(entries).as("telefone, e-mail e Instagram").isEqualTo(3);

        // Mesma empresa cadastrada de novo (ex.: nova importacao): o bloqueio continua.
        String again = createLead(session, "35988881234", null, null);
        generate(again, "WHATSAPP").andExpect(status().isConflict());
        perform(post("/leads/" + again + "/interactions"), session, """
                {"type": "CONTACT", "channel": "WHATSAPP", "direction": "OUTBOUND", "occurredAt": "%s"}
                """.formatted(Instant.now()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("mover o lead para DO_NOT_CONTACT tambem alimenta a lista")
    void statusChangeSuppressesIdentifiers() throws Exception {
        moveTo(leadId, "DO_NOT_CONTACT");

        String again = createLead(session, null, EMAIL, null);
        generate(again, "EMAIL").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("registro e idempotente e identificador invalido e recusado")
    void registrationRules() throws Exception {
        String first = JsonPath.read(suppress(session, "EMAIL", EMAIL, null).andReturn().getResponse().getContentAsString(), "$.id");
        String second = JsonPath.read(suppress(session, "EMAIL", " " + EMAIL.toUpperCase() + " ", null)
                .andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(second).isEqualTo(first);

        suppress(session, "PHONE", "98888-1234", null).andExpect(status().isBadRequest());
        suppress(session, "INSTAGRAM", "https://instagram.com/p/abc", null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("so administrador consulta a lista; operador pode registrar")
    void listingIsRestricted() throws Exception {
        MockHttpSession operator = TestSessions.login(mockMvc, testData.createActiveOperator("outreach-op"));
        suppress(operator, "EMAIL", "naocontatar@example.test", null).andExpect(status().isCreated());

        mockMvc.perform(get("/suppression-entries").session(operator)).andExpect(status().isForbidden());

        suppress(session, "EMAIL", "outra@example.test", null).andExpect(status().isCreated());
        mockMvc.perform(get("/suppression-entries").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].identifier").value("outra@example.test"));
    }

    @Test
    @DisplayName("registro de bloqueio vai para a auditoria sem o identificador")
    void suppressionIsAudited() throws Exception {
        String id = JsonPath.read(suppress(session, "EMAIL", "auditada@example.test", "EMAIL")
                .andReturn().getResponse().getContentAsString(), "$.id");

        var audit = jdbc.queryForMap(
                "select action, detail from audit_event where resource_id = ?", id);
        assertThat(audit).containsEntry("action", "SUPPRESSION_CREATE").containsEntry("detail", "EMAIL");
    }
}
