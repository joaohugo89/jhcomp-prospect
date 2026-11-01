package com.jhcomp.prospect.dashboard;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyOriginSql;
import com.jhcomp.prospect.shared.text.Texts;

/**
 * Trechos de SQL comuns as consultas do dashboard. Um lugar so para os
 * filtros: resumo, serie, conversao, atencao e funil contam com as mesmas
 * regras, e os numeros de uma secao batem com os da outra.
 */
final class DashboardSql {

    private DashboardSql() {
    }

    /** Filtros. Periodo semiaberto: {@code [from, to)}. Campos nulos nao filtram. */
    record Filters(
            UUID organizationId, Instant from, Instant to, String segment, String city, SourceType source,
            UUID assignee) {}

    /** Tipo da primeira proveniencia gravada da empresa {@code c} (D29). */
    static final String ORIGIN = CompanyOriginSql.ORIGIN;

    /** Empresa {@code c} dentro dos filtros de segmento, cidade e origem. */
    private static final String COMPANY = """
            (cast(:segment as text) is null or %s)
            and (cast(:city as text) is null or lower(c.city) = lower(:city))
            and (cast(:source as text) is null or %s = :source)
            """.formatted(CompanyOriginSql.SEGMENT_EQUALS, ORIGIN);

    /** Lead {@code l} da empresa {@code c}: filtros da empresa e responsavel do lead. */
    static final String LEAD_FILTER = COMPANY + """
            and (cast(:assignee as uuid) is null or l.assigned_user_id = :assignee)
            """;

    /**
     * Empresa {@code c} sem lead na consulta (empresas adicionadas). Com
     * responsavel, conta a empresa que tem algum lead dele.
     */
    static final String COMPANY_FILTER = COMPANY + """
            and (cast(:assignee as uuid) is null or exists (
                select 1 from lead la
                 where la.organization_id = c.organization_id and la.company_id = c.id
                   and la.assigned_user_id = :assignee))
            """;

    static MapSqlParameterSource params(Filters filters) {
        return new MapSqlParameterSource()
                .addValue("organizationId", filters.organizationId())
                .addValue("from", Timestamp.from(filters.from()))
                .addValue("to", Timestamp.from(filters.to()))
                .addValue("segment", Texts.blankToNull(filters.segment()))
                .addValue("city", Texts.blankToNull(filters.city()))
                .addValue("source", filters.source() == null ? null : filters.source().name())
                .addValue("assignee", filters.assignee());
    }
}
