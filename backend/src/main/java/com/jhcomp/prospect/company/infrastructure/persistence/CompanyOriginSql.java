package com.jhcomp.prospect.company.infrastructure.persistence;

/**
 * A company's origin in SQL: the type of its first recorded provenance, the
 * path by which it entered the system (D29). An import that later completes
 * the company does not change the origin.
 *
 * <p>One place for the rule: the dashboard counts by it and the lead queue
 * filters by it, so the number the dashboard shows matches the list it
 * opens.
 */
public final class CompanyOriginSql {

    private CompanyOriginSql() {
    }

    /** A subquery over the company aliased {@code c}. */
    public static final String ORIGIN = """
            (select s.source_type from lead_source s
              where s.organization_id = c.organization_id and s.company_id = c.id
              order by s.recorded_seq limit 1)""";

    /**
     * Company {@code c}'s segment equals the {@code :segment} parameter,
     * ignoring case and surrounding spaces: "Barbearia" and " barbearia" are
     * the same segment.
     */
    public static final String SEGMENT_EQUALS = "lower(btrim(c.segment)) = lower(btrim(:segment))";

    /** Company {@code c} has no segment recorded. */
    public static final String WITHOUT_SEGMENT = "(c.segment is null or btrim(c.segment) = '')";
}
