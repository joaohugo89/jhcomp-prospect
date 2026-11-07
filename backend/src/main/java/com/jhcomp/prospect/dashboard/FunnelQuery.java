package com.jhcomp.prospect.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.lead.domain.LeadStatus;

/**
 * The conversion funnel and its velocity (RF-071, D29).
 *
 * <p>Cohort: leads opened in the period, within the filters. A stage's reach:
 * how many got to it or beyond, by the status history; won counts as having
 * passed through all of them. Lost and blocked advance nobody: a lead lost
 * after the proposal did reach the proposal, and that is what counts.
 *
 * <p>Velocity: the average days spent in a stage by those who left it,
 * between the entry and the next change (by recording order). Those still in
 * the stage are excluded: their time has not ended. Below
 * {@link DashboardQuery#MINIMUM_SAMPLE} exits, the average is not
 * presented.
 */
@Repository
public class FunnelQuery {

    /** The funnel's stages, in order; positions run from 1 to 7. */
    static final List<LeadStatus> STAGES = List.of(
            LeadStatus.NEW,
            LeadStatus.QUALIFIED,
            LeadStatus.CONTACT_APPROVED,
            LeadStatus.CONTACTED,
            LeadStatus.IN_CONVERSATION,
            LeadStatus.PROPOSAL_SENT,
            LeadStatus.WON);

    /**
     * {@code reached}: leads in the cohort that got to this stage or beyond.
     * {@code avgDays}: the average stay of those who have left (absent without
     * a sample).
     */
    public record Stage(
            String status, long reached, long exits, BigDecimal avgDays, boolean sufficientSample) {}

    public record Funnel(int minimumSample, long cohort, List<Stage> stages) {}

    private final NamedParameterJdbcTemplate jdbc;

    FunnelQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Funnel funnel(DashboardSql.Filters filters) {
        MapSqlParameterSource params = DashboardSql.params(filters);
        String cohort = """
                cohort as (
                    select l.id
                      from lead l join company c on c.id = l.company_id
                     where l.organization_id = :organizationId
                       and l.created_at >= :from and l.created_at < :to and %s
                ),
                changes as (
                    select s.lead_id, s.from_status, s.to_status, s.changed_at,
                           lag(s.changed_at) over (partition by s.lead_id order by s.recorded_seq) as entered_at
                      from lead_status_change s
                     where s.organization_id = :organizationId and s.lead_id in (select id from cohort)
                )""".formatted(DashboardSql.LEAD_FILTER);

        // The stage's position in the funnel; lost and blocked stay null and do not count as progress.
        String position = "case ch.to_status %s end".formatted(String.join(" ", STAGES.stream()
                .map(stage -> "when '%s' then %d".formatted(stage.name(), STAGES.indexOf(stage) + 1))
                .toList()));

        Map<String, Object> reach = jdbc.queryForMap("""
                with %s,
                furthest as (
                    select c.id, coalesce(max(%s), 1) as position
                      from cohort c left join changes ch on ch.lead_id = c.id
                     group by c.id
                )
                select count(*) as cohort,
                       %s
                  from furthest
                """.formatted(cohort, position, reachColumns()), params);

        Map<String, Long> exits = new HashMap<>();
        Map<String, BigDecimal> averages = new HashMap<>();
        jdbc.query("""
                with %s
                select from_status, count(*) as exits,
                       avg(extract(epoch from changed_at - entered_at)) / 86400 as avg_days
                  from changes
                 where from_status is not null and entered_at is not null
                 group by from_status
                """.formatted(cohort), params, row -> {
                    exits.put(row.getString("from_status"), row.getLong("exits"));
                    BigDecimal days = row.getBigDecimal("avg_days");
                    if (days != null) {
                        averages.put(row.getString("from_status"), days.setScale(1, RoundingMode.HALF_UP));
                    }
                });

        List<Stage> stages = new ArrayList<>(STAGES.size());
        for (int i = 0; i < STAGES.size(); i++) {
            String status = STAGES.get(i).name();
            long out = exits.getOrDefault(status, 0L);
            boolean sufficient = out >= DashboardQuery.MINIMUM_SAMPLE;
            stages.add(new Stage(
                    status,
                    ((Number) reach.get("reached_" + (i + 1))).longValue(),
                    out,
                    sufficient ? averages.get(status) : null,
                    sufficient));
        }
        return new Funnel(DashboardQuery.MINIMUM_SAMPLE, ((Number) reach.get("cohort")).longValue(), stages);
    }

    private static String reachColumns() {
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= STAGES.size(); i++) {
            columns.add("count(*) filter (where position >= %d) as reached_%d".formatted(i, i));
        }
        return String.join(",\n       ", columns);
    }
}
