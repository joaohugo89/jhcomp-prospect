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
 * Funil de conversao e velocidade (RF-071, D29).
 *
 * <p>Coorte: leads abertos no periodo, nos filtros. Alcance de uma etapa:
 * quantos chegaram a ela ou alem, pelo historico de status; ganho conta como
 * tendo passado por todas. Perdido e bloqueado nao avancam ninguem: um lead
 * perdido depois da proposta alcancou a proposta, e e isso que conta.
 *
 * <p>Velocidade: media de dias que quem saiu de uma etapa ficou nela, entre a
 * entrada e a mudanca seguinte (ordem de gravacao). Quem ainda esta na etapa
 * nao entra: o tempo dele nao terminou. Abaixo de
 * {@link DashboardQuery#MINIMUM_SAMPLE} saidas, a media nao e apresentada.
 */
@Repository
public class FunnelQuery {

    /** Etapas do funil, na ordem; a posicao e 1 a 7. */
    static final List<LeadStatus> STAGES = List.of(
            LeadStatus.NEW,
            LeadStatus.QUALIFIED,
            LeadStatus.CONTACT_APPROVED,
            LeadStatus.CONTACTED,
            LeadStatus.IN_CONVERSATION,
            LeadStatus.PROPOSAL_SENT,
            LeadStatus.WON);

    /**
     * {@code reached}: leads da coorte que chegaram a esta etapa ou alem.
     * {@code avgDays}: media de permanencia de quem ja saiu (ausente sem amostra).
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

        // Posicao da etapa no funil; perdido e bloqueado ficam nulos e nao contam como avanco.
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
