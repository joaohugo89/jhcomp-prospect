package com.jhcomp.prospect.dashboard;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Needs attention": what the operator should do now (doc 08 §4).
 *
 * <p>Four sections, each with the total and the {@code limit} most urgent
 * items; the rest is found through the lead. The situation now, with no
 * period: an overdue task does not stop being overdue because the filter
 * covers the last 7 days. A lead in {@code DO_NOT_CONTACT} is excluded from
 * all of them: the panel never pushes an approach at someone who asked not to
 * be contacted (rule 4).
 */
@Repository
public class AttentionQuery {

    /** Days in an in-progress stage after which the lead counts as stalled (the Kanban's threshold). */
    static final int STALE_DAYS = 7;

    /** Days without a touch on a proposal: the money closest to going cold. */
    static final int QUIET_PROPOSAL_DAYS = 7;

    static final int MAX_LIMIT = 20;

    public record Item(UUID leadId, String companyName, String status, Instant at, String detail, boolean overdue) {}

    public record Section(long total, List<Item> items) {}

    public record Attention(Section tasks, Section staleLeads, Section quietProposals, Section withoutTask) {}

    /** Each lead's last status change, by recording order: the entry into the current stage. */
    private static final String LATEST_CHANGE = """
            latest as (
                select distinct on (s.lead_id) s.lead_id, s.changed_at
                  from lead_status_change s
                 where s.organization_id = :organizationId
                 order by s.lead_id, s.recorded_seq desc
            )""";

    /** The stages in progress; {@code NEW} is triage, with no next step committed yet. */
    private static final String IN_PROGRESS =
            "l.status in ('QUALIFIED', 'CONTACT_APPROVED', 'CONTACTED', 'IN_CONVERSATION', 'PROPOSAL_SENT')";

    private final NamedParameterJdbcTemplate jdbc;

    AttentionQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param endOfToday the end of today in the operator's time zone, so a task
     *     due today appears alongside the overdue ones
     */
    @Transactional(readOnly = true)
    public Attention attention(DashboardSql.Filters filters, Instant now, Instant endOfToday, int limit) {
        MapSqlParameterSource params = DashboardSql.params(filters)
                .addValue("now", Timestamp.from(now))
                .addValue("endOfToday", Timestamp.from(endOfToday))
                .addValue("staleBefore", Timestamp.from(now.minus(Duration.ofDays(STALE_DAYS))))
                .addValue("quietBefore", Timestamp.from(now.minus(Duration.ofDays(QUIET_PROPOSAL_DAYS))))
                .addValue("limit", Math.clamp(limit, 1, MAX_LIMIT));

        Section tasks = section("""
                select l.id as lead_id, c.name as company_name, l.status, t.due_at as at, t.title as detail,
                       t.due_at < :now as overdue, count(*) over () as total
                  from task t join lead l on l.id = t.lead_id join company c on c.id = l.company_id
                 where t.organization_id = :organizationId and t.status = 'OPEN'
                   and t.due_at < :endOfToday and l.status <> 'DO_NOT_CONTACT' and %s
                 order by t.due_at, t.id
                 limit :limit
                """.formatted(DashboardSql.LEAD_FILTER), params);

        Section stale = section("""
                with %s
                select l.id as lead_id, c.name as company_name, l.status, latest.changed_at as at,
                       null as detail, false as overdue, count(*) over () as total
                  from lead l join company c on c.id = l.company_id
                  join latest on latest.lead_id = l.id
                 where l.organization_id = :organizationId and %s
                   and latest.changed_at <= :staleBefore and %s
                 order by latest.changed_at, l.id
                 limit :limit
                """.formatted(LATEST_CHANGE, IN_PROGRESS, DashboardSql.LEAD_FILTER), params);

        // Last touch: the most recent interaction or, failing that, the arrival at the proposal.
        Section quiet = section("""
                with %s
                select l.id as lead_id, c.name as company_name, l.status, touch.at, null as detail,
                       false as overdue, count(*) over () as total
                  from lead l join company c on c.id = l.company_id
                  join latest on latest.lead_id = l.id
                  cross join lateral (
                      select greatest(latest.changed_at, coalesce(max(i.occurred_at), latest.changed_at)) as at
                        from interaction i
                       where i.organization_id = l.organization_id and i.lead_id = l.id
                  ) touch
                 where l.organization_id = :organizationId and l.status = 'PROPOSAL_SENT'
                   and touch.at <= :quietBefore and %s
                 order by touch.at, l.id
                 limit :limit
                """.formatted(LATEST_CHANGE, DashboardSql.LEAD_FILTER), params);

        Section withoutTask = section("""
                select l.id as lead_id, c.name as company_name, l.status, l.updated_at as at, null as detail,
                       false as overdue, count(*) over () as total
                  from lead l join company c on c.id = l.company_id
                 where l.organization_id = :organizationId and %s
                   and not exists (select 1 from task t
                                    where t.organization_id = l.organization_id and t.lead_id = l.id
                                      and t.status = 'OPEN')
                   and %s
                 order by l.updated_at, l.id
                 limit :limit
                """.formatted(IN_PROGRESS, DashboardSql.LEAD_FILTER), params);

        return new Attention(tasks, stale, quiet, withoutTask);
    }

    private Section section(String sql, MapSqlParameterSource params) {
        List<Item> items = new ArrayList<>();
        long[] total = {0};
        jdbc.query(sql, params, row -> {
            total[0] = row.getLong("total");
            items.add(new Item(
                    row.getObject("lead_id", UUID.class),
                    row.getString("company_name"),
                    row.getString("status"),
                    row.getTimestamp("at").toInstant(),
                    row.getString("detail"),
                    row.getBoolean("overdue")));
        });
        return new Section(total[0], List.copyOf(items));
    }
}
