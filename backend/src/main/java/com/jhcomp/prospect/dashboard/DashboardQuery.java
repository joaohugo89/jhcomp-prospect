package com.jhcomp.prospect.dashboard;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.shared.text.Texts;

/**
 * The dashboard's aggregate queries (RF-070, doc 08 §4).
 *
 * <p>Plain SQL: these are counts across several tables that no entity
 * represents. Every query filters by organisation; segment, city and origin
 * filter through the lead's company.
 *
 * <p>A company's origin is the type of its first recorded provenance: the path
 * by which it entered the system. An import that later completes the company
 * changes neither the origin nor counts the company twice (D29).
 */
@Repository
public class DashboardQuery {

    public record Summary(
            Map<String, Long> leadsByStatus,
            long activeLeads,
            long companiesAdded,
            long contactsRegistered,
            long openProposals,
            long dealsWon,
            BigDecimal wonValue,
            long openTasks,
            long overdueTasks,
            long companiesWithoutSegment) {}

    public record Activity(
            String type, UUID leadId, String companyName, Instant occurredAt, String actorName, String fromStatus,
            String toStatus, String interactionType, String channel) {}

    /**
     * One group's conversion (RF-071). Denominator: leads opened in the
     * period. Contacted: with a recorded outbound approach; won: currently in
     * {@code WON}. Below {@link #MINIMUM_SAMPLE} leads the rate is not
     * presented at all.
     */
    public record ConversionRow(String key, long leads, long contacted, long won, boolean sufficientSample) {}

    public record Conversion(int minimumSample, List<ConversionRow> bySource, List<ConversionRow> bySegment) {}

    /** Below this, a single lead moves the rate by more than 10 points. */
    static final int MINIMUM_SAMPLE = 10;

    /** The segment is free text; the panel shows only the largest groups. */
    static final int MAX_SEGMENTS = 10;

    /** The interval size of the time series. */
    public enum Granularity {
        DAY("day"),
        WEEK("week"),
        MONTH("month");

        private final String unit;

        Granularity(String unit) {
            this.unit = unit;
        }
    }

    /**
     * One interval of the series. {@code start} is the interval's first day
     * inside the period: the first week or month may start later than its
     * natural beginning.
     */
    public record TimelinePoint(
            LocalDate start, long companiesAdded, long contactsRegistered, long dealsWon, BigDecimal wonValue) {}

    /** Outside the active funnel: closed and blocked. */
    private static final EnumSet<LeadStatus> INACTIVE = EnumSet.of(LeadStatus.WON, LeadStatus.LOST, LeadStatus.DO_NOT_CONTACT);

    private final NamedParameterJdbcTemplate jdbc;

    DashboardQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Summary summary(DashboardSql.Filters filters, Instant now) {
        MapSqlParameterSource params = DashboardSql.params(filters).addValue("now", Timestamp.from(now));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("""
                select l.status, count(*) as total
                  from lead l join company c on c.id = l.company_id
                 where l.organization_id = :organizationId and %s
                 group by l.status
                """.formatted(DashboardSql.LEAD_FILTER), params,
                row -> {
                    byStatus.put(row.getString("status"), row.getLong("total"));
                });

        long active = byStatus.entrySet().stream()
                .filter(entry -> !INACTIVE.contains(LeadStatus.valueOf(entry.getKey())))
                .mapToLong(Map.Entry::getValue)
                .sum();

        long companies = count("""
                select count(*) from company c
                 where c.organization_id = :organizationId
                   and c.created_at >= :from and c.created_at < :to and %s
                """.formatted(DashboardSql.COMPANY_FILTER), params);

        // A recorded contact: an outbound approach the operator confirmed having made.
        long contacts = count("""
                select count(*) from interaction i
                  join lead l on l.id = i.lead_id join company c on c.id = l.company_id
                 where i.organization_id = :organizationId
                   and i.interaction_type = 'CONTACT' and i.direction = 'OUTBOUND'
                   and i.occurred_at >= :from and i.occurred_at < :to and %s
                """.formatted(DashboardSql.LEAD_FILTER), params);

        Map<String, Object> won = jdbc.queryForMap("""
                select count(*) as total, coalesce(sum(d.estimated_value), 0) as value
                  from lead l join company c on c.id = l.company_id
                  left join deal d on d.organization_id = l.organization_id and d.lead_id = l.id
                 where l.organization_id = :organizationId and l.status = 'WON'
                   and l.closed_at >= :from and l.closed_at < :to and %s
                """.formatted(DashboardSql.LEAD_FILTER), params);

        Map<String, Object> tasks = jdbc.queryForMap("""
                select count(*) as open, count(*) filter (where t.due_at < :now) as overdue
                  from task t join lead l on l.id = t.lead_id join company c on c.id = l.company_id
                 where t.organization_id = :organizationId and t.status = 'OPEN' and %s
                """.formatted(DashboardSql.LEAD_FILTER), params);

        // Data quality: with no segment, conversion by segment stops meaning anything.
        long withoutSegment = count("""
                select count(*) from company c
                 where c.organization_id = :organizationId and c.archived_at is null
                   and (c.segment is null or btrim(c.segment) = '') and %s
                """.formatted(DashboardSql.COMPANY_FILTER), params);

        return new Summary(
                byStatus,
                active,
                companies,
                contacts,
                byStatus.getOrDefault("PROPOSAL_SENT", 0L),
                ((Number) won.get("total")).longValue(),
                (BigDecimal) won.get("value"),
                ((Number) tasks.get("open")).longValue(),
                ((Number) tasks.get("overdue")).longValue(),
                withoutSegment);
    }

    /** Conversion of the leads opened in the period, by origin and by segment (RF-071). */
    @Transactional(readOnly = true)
    public Conversion conversion(DashboardSql.Filters filters) {
        MapSqlParameterSource params = DashboardSql.params(filters).addValue("maxSegments", MAX_SEGMENTS);
        String cohort = """
                select %s as origin, c.segment,
                       exists (select 1 from interaction i
                                where i.organization_id = l.organization_id and i.lead_id = l.id
                                  and i.interaction_type = 'CONTACT' and i.direction = 'OUTBOUND') as contacted,
                       l.status = 'WON' as won
                  from lead l join company c on c.id = l.company_id
                 where l.organization_id = :organizationId
                   and l.created_at >= :from and l.created_at < :to and %s
                """.formatted(DashboardSql.ORIGIN, DashboardSql.LEAD_FILTER);
        String totals = "count(*) as leads, count(*) filter (where contacted) as contacted,"
                + " count(*) filter (where won) as won";

        List<ConversionRow> bySource = jdbc.query(
                "select origin as key, " + totals + " from (" + cohort + ") k"
                        + " group by origin order by leads desc, origin",
                params, (row, rowNumber) -> conversionRow(row));
        List<ConversionRow> bySegment = jdbc.query(
                "select segment as key, " + totals + " from (" + cohort + ") k"
                        + " group by segment order by leads desc, segment nulls last limit :maxSegments",
                params, (row, rowNumber) -> conversionRow(row));
        return new Conversion(MINIMUM_SAMPLE, bySource, bySegment);
    }

    private static ConversionRow conversionRow(ResultSet row) throws SQLException {
        long leads = row.getLong("leads");
        return new ConversionRow(
                row.getString("key"), leads, row.getLong("contacted"), row.getLong("won"), leads >= MINIMUM_SAMPLE);
    }

    /**
     * The period metrics of {@link #summary} in intervals (day, week or
     * calendar month in {@code zone}), under the same definitions and filters.
     * Every interval of the period appears, the empty ones included: a gap in
     * the chart would read as missing data rather than as zero.
     */
    @Transactional(readOnly = true)
    public List<TimelinePoint> timeline(
            DashboardSql.Filters filters, LocalDate from, LocalDate to, Granularity granularity, ZoneId zone) {
        MapSqlParameterSource params = DashboardSql.params(filters)
                .addValue("unit", granularity.unit)
                .addValue("step", "1 " + granularity.unit)
                .addValue("zone", zone.getId())
                .addValue("firstDay", Date.valueOf(from))
                .addValue("lastDay", Date.valueOf(to));
        String bucket = "cast(date_trunc(:unit, %s at time zone :zone) as date)";
        String sql = """
                with buckets as (
                    select cast(b as date) as bucket
                      from generate_series(date_trunc(:unit, cast(:firstDay as timestamp)),
                                           cast(:lastDay as timestamp), cast(:step as interval)) b
                ),
                companies as (
                    select %1$s as bucket, count(*) as total
                      from company c
                     where c.organization_id = :organizationId
                       and c.created_at >= :from and c.created_at < :to and %5$s
                     group by 1
                ),
                contacts as (
                    select %2$s as bucket, count(*) as total
                      from interaction i
                      join lead l on l.id = i.lead_id join company c on c.id = l.company_id
                     where i.organization_id = :organizationId
                       and i.interaction_type = 'CONTACT' and i.direction = 'OUTBOUND'
                       and i.occurred_at >= :from and i.occurred_at < :to and %4$s
                     group by 1
                ),
                won as (
                    select %3$s as bucket, count(*) as total, coalesce(sum(d.estimated_value), 0) as value
                      from lead l join company c on c.id = l.company_id
                      left join deal d on d.organization_id = l.organization_id and d.lead_id = l.id
                     where l.organization_id = :organizationId and l.status = 'WON'
                       and l.closed_at >= :from and l.closed_at < :to and %4$s
                     group by 1
                )
                select greatest(b.bucket, cast(:firstDay as date)) as start,
                       coalesce(co.total, 0) as companies, coalesce(ct.total, 0) as contacts,
                       coalesce(w.total, 0) as won, coalesce(w.value, 0) as won_value
                  from buckets b
                  left join companies co on co.bucket = b.bucket
                  left join contacts ct on ct.bucket = b.bucket
                  left join won w on w.bucket = b.bucket
                 order by b.bucket
                """.formatted(
                        bucket.formatted("c.created_at"),
                        bucket.formatted("i.occurred_at"),
                        bucket.formatted("l.closed_at"),
                        DashboardSql.LEAD_FILTER,
                        DashboardSql.COMPANY_FILTER);
        return jdbc.query(sql, params, (row, rowNumber) -> new TimelinePoint(
                row.getObject("start", LocalDate.class),
                row.getLong("companies"),
                row.getLong("contacts"),
                row.getLong("won"),
                row.getBigDecimal("won_value")));
    }

    /** Recent activity: status changes and interactions, newest first. */
    @Transactional(readOnly = true)
    public Page<Activity> activity(DashboardSql.Filters filters, Pageable pageable) {
        String events = """
                select 'STATUS_CHANGE' as type, s.lead_id, c.name as company_name, s.changed_at as occurred_at,
                       s.recorded_seq, u.display_name as actor_name, s.from_status, s.to_status,
                       null as interaction_type, null as channel
                  from lead_status_change s
                  join lead l on l.id = s.lead_id join company c on c.id = l.company_id
                  join app_user u on u.id = s.changed_by_user_id
                 where s.organization_id = :organizationId and s.changed_at >= :from and s.changed_at < :to and %1$s
                union all
                select 'INTERACTION', i.lead_id, c.name, i.occurred_at, i.recorded_seq, u.display_name, null, null,
                       i.interaction_type, i.channel
                  from interaction i
                  join lead l on l.id = i.lead_id join company c on c.id = l.company_id
                  join app_user u on u.id = i.created_by_user_id
                 where i.organization_id = :organizationId and i.occurred_at >= :from and i.occurred_at < :to and %1$s
                """.formatted(DashboardSql.LEAD_FILTER);
        MapSqlParameterSource params = DashboardSql.params(filters)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        List<Activity> items = jdbc.query(
                "select * from (" + events + ") e order by occurred_at desc, recorded_seq desc limit :limit offset :offset",
                params,
                (row, rowNumber) -> new Activity(
                        row.getString("type"),
                        row.getObject("lead_id", UUID.class),
                        row.getString("company_name"),
                        row.getTimestamp("occurred_at").toInstant(),
                        row.getString("actor_name"),
                        row.getString("from_status"),
                        row.getString("to_status"),
                        row.getString("interaction_type"),
                        row.getString("channel")));
        long total = count("select count(*) from (" + events + ") e", params);
        return new PageImpl<>(items, pageable, total);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }
}
