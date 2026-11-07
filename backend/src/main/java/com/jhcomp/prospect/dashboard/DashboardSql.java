package com.jhcomp.prospect.dashboard;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyOriginSql;
import com.jhcomp.prospect.shared.text.Texts;

/**
 * SQL fragments shared by the dashboard's queries. One place for the filters:
 * summary, series, conversion, attention and funnel count under the same
 * rules, so the numbers of one section match another's.
 */
final class DashboardSql {

    private DashboardSql() {
    }

    /** The filters. A half-open period: {@code [from, to)}. Null fields filter nothing. */
    record Filters(
            UUID organizationId, Instant from, Instant to, String segment, String city, SourceType source,
            UUID assignee) {}

    /** The type of company {@code c}'s first recorded provenance (D29). */
    static final String ORIGIN = CompanyOriginSql.ORIGIN;

    /** Company {@code c} within the segment, city and origin filters. */
    private static final String COMPANY = """
            (cast(:segment as text) is null or %s)
            and (cast(:city as text) is null or lower(c.city) = lower(:city))
            and (cast(:source as text) is null or %s = :source)
            """.formatted(CompanyOriginSql.SEGMENT_EQUALS, ORIGIN);

    /** Lead {@code l} of company {@code c}: the company's filters plus the lead's owner. */
    static final String LEAD_FILTER = COMPANY + """
            and (cast(:assignee as uuid) is null or l.assigned_user_id = :assignee)
            """;

    /**
     * Company {@code c} with no lead in the query (companies added). With an
     * owner, it counts a company that has any lead of theirs.
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
