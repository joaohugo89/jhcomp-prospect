package com.jhcomp.prospect.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * Revalidacao da sessao a cada requisicao.
 *
 * <p>Sem ela, {@code organizationId} e {@code role} ficariam congelados na
 * sessao por horas: revogar associacao, desabilitar usuario, suspender
 * organizacao ou rebaixar um administrador nao teria efeito algum ate a sessao
 * expirar. O critério de aceite do RF-003 exige que operacao administrativa
 * seja bloqueada para quem nao tem permissao, e "daqui a oito horas" nao
 * satisfaz esse critério.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Revalidacao de sessao")
class SessionRevalidationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    @Test
    @DisplayName("associacao revogada encerra a sessao na requisicao seguinte")
    void revokedMembershipEndsSession() throws Exception {
        var admin = testData.createActiveAdmin("revalid-revoke");
        var session = TestSessions.login(mockMvc, admin);

        // A sessao funciona antes da revogacao.
        mockMvc.perform(get("/auth/me").session(session)).andExpect(status().isOk());

        testData.revokeMembership(admin.memberId());

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("usuario desabilitado encerra a sessao na requisicao seguinte")
    void disabledUserEndsSession() throws Exception {
        var admin = testData.createActiveAdmin("revalid-disable");
        var session = TestSessions.login(mockMvc, admin);

        testData.disableUser(admin.userId());

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("organizacao suspensa encerra a sessao na requisicao seguinte")
    void suspendedOrganizationEndsSession() throws Exception {
        var admin = testData.createActiveAdmin("revalid-suspend");
        var session = TestSessions.login(mockMvc, admin);

        testData.suspendOrganization(admin.organizationId());

        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rebaixar administrador a operador bloqueia a operacao administrativa de imediato")
    void demotionTakesEffectImmediately() throws Exception {
        var admin = testData.createActiveAdmin("revalid-demote");
        var session = TestSessions.login(mockMvc, admin);

        // Enquanto e administrador, lista membros.
        mockMvc.perform(get("/organizations/members").session(session)).andExpect(status().isOk());

        testData.changeRole(admin.memberId(), OrganizationRole.OPERATOR);

        // Rebaixado: a mesma sessao perde o acesso, sem precisar expirar.
        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("FORBIDDEN"));

        // E o papel exibido tambem acompanha a mudanca.
        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    @DisplayName("promover operador a administrador libera a operacao de imediato")
    void promotionTakesEffectImmediately() throws Exception {
        var operator = testData.createActiveOperator("revalid-promote");
        var session = TestSessions.login(mockMvc, operator);

        mockMvc.perform(get("/organizations/members").session(session)).andExpect(status().isForbidden());

        testData.changeRole(operator.memberId(), OrganizationRole.ADMIN);

        mockMvc.perform(get("/organizations/members").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("sessao valida continua funcionando entre requisicoes")
    void validSessionKeepsWorking() throws Exception {
        var admin = testData.createActiveAdmin("revalid-ok");
        var session = TestSessions.login(mockMvc, admin);

        // A revalidacao nao deve derrubar sessao legitima.
        for (int request = 0; request < 3; request++) {
            mockMvc.perform(get("/auth/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.organizationId").value(admin.organizationId().toString()));
        }
    }
}
