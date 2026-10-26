package com.jhcomp.prospect.shared;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jhcomp.prospect.identity.IdentityTestData;
import com.jhcomp.prospect.identity.IdentityTestData.Member;

/**
 * O banco recusa associacao entre organizacoes diferentes (V4, doc 04 §4).
 *
 * <p>Usa SQL direto de proposito: o objetivo e provar que a trava vale mesmo
 * quando a aplicacao erra, ou seja, sem passar por ela.
 */
@Import(IdentityTestData.class)
@DisplayName("Migration V4 (chaves compostas de tenant)")
class TenantIntegrityMigrationTest extends PostgresIntegrationTest {

    private final JdbcTemplate jdbc;

    @Autowired
    private IdentityTestData testData;

    private Member memberA;
    private Member memberB;
    private UUID companyA;
    private UUID companyB;

    TenantIntegrityMigrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        memberA = testData.createActiveAdmin("tenant-a");
        memberB = testData.createActiveAdmin("tenant-b");
        companyA = insertCompany(memberA.organizationId());
        companyB = insertCompany(memberB.organizationId());
    }

    @Test
    @DisplayName("lead nao referencia empresa de outra organizacao")
    void leadRejectsForeignCompany() {
        assertThatThrownBy(() -> insertLead(memberB.organizationId(), companyA, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("lead_company_same_org");
    }

    @Test
    @DisplayName("lead nao e atribuido a usuario que nao e membro da organizacao")
    void leadRejectsForeignAssignee() {
        assertThatThrownBy(() -> insertLead(memberB.organizationId(), companyB, memberA.userId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("lead_assigned_member_same_org");
    }

    @Test
    @DisplayName("historico de status nao referencia lead de outra organizacao")
    void statusChangeRejectsForeignLead() {
        UUID leadA = insertLead(memberA.organizationId(), companyA, null);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into lead_status_change (id, organization_id, lead_id, to_status, changed_by_user_id)"
                                + " values (?, ?, ?, 'NEW', ?)",
                        UUID.randomUUID(), memberB.organizationId(), leadA, memberB.userId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("lead_status_change_lead_same_org");
    }

    @Test
    @DisplayName("proveniencia nao referencia empresa de outra organizacao")
    void leadSourceRejectsForeignCompany() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into lead_source (id, organization_id, company_id, source_type, source_name, collected_at)"
                                + " values (?, ?, ?, 'MANUAL', 'Cadastro manual', now())",
                        UUID.randomUUID(), memberB.organizationId(), companyA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("lead_source_company_same_org");
    }

    @Test
    @DisplayName("associacoes dentro da mesma organizacao continuam aceitas")
    void sameOrganizationIsAccepted() {
        assertThatCode(() -> {
                    UUID lead = insertLead(memberA.organizationId(), companyA, memberA.userId());
                    jdbc.update(
                            "insert into lead_source (id, organization_id, company_id, lead_id, source_type, source_name,"
                                    + " collected_at, collected_by_user_id)"
                                    + " values (?, ?, ?, ?, 'MANUAL', 'Cadastro manual', now(), ?)",
                            UUID.randomUUID(), memberA.organizationId(), companyA, lead, memberA.userId());
                })
                .doesNotThrowAnyException();
    }

    private UUID insertCompany(UUID organizationId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into company (id, organization_id, name, normalized_name, city, state)"
                        + " values (?, ?, 'Padaria Ficticia', 'padaria ficticia', 'Cidade Exemplo', 'MG')",
                id, organizationId);
        return id;
    }

    private UUID insertLead(UUID organizationId, UUID companyId, UUID assignedUserId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into lead (id, organization_id, company_id, status, priority, assigned_user_id)"
                        + " values (?, ?, ?, 'NEW', 'MEDIUM', ?)",
                id, organizationId, companyId, assignedUserId);
        return id;
    }
}
