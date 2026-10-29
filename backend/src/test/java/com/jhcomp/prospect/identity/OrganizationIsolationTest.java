package com.jhcomp.prospect.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import com.jhcomp.prospect.identity.domain.MemberStatus;
import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;
import com.jhcomp.prospect.shared.TestSessions;

/**
 * Isolamento entre organizacoes (RNF-001, doc 10 §3).
 *
 * <p>Este e o teste que o doc 10 §3 chama de critico: duas organizacoes, A e B,
 * e a verificacao de que nenhum caminho leva um usuario de A a dado de B — nem
 * por listagem, nem por UUID conhecido, nem por papel administrativo.
 *
 * <p>Verifica tanto a resposta HTTP quanto o comportamento no banco, como o
 * documento exige.
 */
@AutoConfigureMockMvc
@Import(IdentityTestData.class)
@DisplayName("Isolamento entre organizacoes")
class OrganizationIsolationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityTestData testData;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    private OrganizationEntity organizationA;
    private OrganizationEntity organizationB;
    private IdentityTestData.Member adminA;
    private IdentityTestData.Member memberOfB;

    @BeforeEach
    void setUp() {
        organizationA = testData.createOrganization("org-a", com.jhcomp.prospect.identity.domain.OrganizationStatus.ACTIVE);
        organizationB = testData.createOrganization("org-b", com.jhcomp.prospect.identity.domain.OrganizationStatus.ACTIVE);

        adminA = testData.createMember(
                organizationA, OrganizationRole.ADMIN, UserStatus.ACTIVE, MemberStatus.ACTIVE);
        memberOfB = testData.createMember(
                organizationB, OrganizationRole.ADMIN, UserStatus.ACTIVE, MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("usuario de A nao ve membros de B na listagem")
    void listingIsScopedToOwnOrganization() throws Exception {
        var session = TestSessions.login(mockMvc, adminA);

        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(adminA.userId().toString()))
                // O membro de B nao pode aparecer em lugar algum da resposta.
                .andExpect(jsonPath("$.content[?(@.email == '" + memberOfB.email() + "')]").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("usuario de A nao acessa membro de B mesmo conhecendo o UUID")
    void cannotReachOtherOrganizationResourceByUuid() throws Exception {
        var session = TestSessions.login(mockMvc, adminA);

        // O UUID e valido e o recurso existe: so nao pertence a organizacao de
        // quem pede. A resposta e 404, nao 403, para nao confirmar a existencia.
        mockMvc.perform(get("/organizations/members/" + memberOfB.memberId()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Member not found"));
    }

    @Test
    @DisplayName("usuario de A acessa o proprio membro pelo UUID")
    void canReachOwnOrganizationResourceByUuid() throws Exception {
        var session = TestSessions.login(mockMvc, adminA);

        mockMvc.perform(get("/organizations/members/" + adminA.memberId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(adminA.memberId().toString()));
    }

    @Test
    @DisplayName("consulta escopada no banco nao retorna recurso de outra organizacao")
    void repositoryQueryIsScoped() {
        // Mesmo efeito verificado uma camada abaixo do HTTP, como pede o doc 10 §3.
        assertThat(memberRepository.findByIdAndOrganization(memberOfB.memberId(), organizationA.getId()))
                .describedAs("membro de B buscado com escopo de A")
                .isEmpty();

        assertThat(memberRepository.findByIdAndOrganization(memberOfB.memberId(), organizationB.getId()))
                .describedAs("membro de B buscado com escopo de B")
                .isPresent();
    }

    @Test
    @DisplayName("listagem no banco nao mistura organizacoes")
    void repositoryListingIsScoped() {
        var membersOfA = memberRepository.findByOrganization(
                organizationA.getId(), MemberStatus.ACTIVE, PageRequest.of(0, 50));

        assertThat(membersOfA.getContent())
                .allSatisfy(member ->
                        assertThat(member.getOrganization().getId()).isEqualTo(organizationA.getId()));
        assertThat(membersOfA.getContent())
                .noneSatisfy(member -> assertThat(member.getId()).isEqualTo(memberOfB.memberId()));
    }

    @Test
    @DisplayName("operador nao executa operacao administrativa")
    void operatorIsBlockedFromAdminOperation() throws Exception {
        var operator = testData.createMember(
                organizationA, OrganizationRole.OPERATOR, UserStatus.ACTIVE, MemberStatus.ACTIVE);

        var session = TestSessions.login(mockMvc, operator);

        // Critério de aceite do RF-003.
        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("sem autenticacao nao ha acesso a dado de organizacao")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/organizations/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/organizations/members/" + memberOfB.memberId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("organizationId enviado pelo cliente e ignorado")
    void clientSuppliedOrganizationIdIsIgnored() throws Exception {
        var session = TestSessions.login(mockMvc, adminA);

        // Tentativa explicita de trocar de organizacao por query string. O
        // parametro nao e lido em lugar algum; o escopo vem da sessao (RF-002).
        mockMvc.perform(get("/organizations/members")
                        .param("organizationId", organizationB.getId().toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(adminA.userId().toString()));
    }

    @Test
    @DisplayName("sessao de B nao enxerga dado de A")
    void reverseDirectionIsAlsoIsolated() throws Exception {
        // Testa o sentido contrario: um isolamento que funciona so em uma
        // direcao normalmente indica filtro aplicado no lugar errado.
        var session = TestSessions.login(mockMvc, memberOfB);

        mockMvc.perform(get("/organizations/members/" + adminA.memberId()).session(session))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/organizations/members").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(memberOfB.userId().toString()));
    }
}
