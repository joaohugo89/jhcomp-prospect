package com.jhcomp.prospect.dashboard;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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

import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.shared.text.Texts;

/**
 * Consultas agregadas do dashboard (RF-070, doc 08 §4).
 *
 * <p>SQL direto: sao contagens sobre varias tabelas, que entidade nenhuma
 * representa. Toda consulta filtra por organizacao; segmento e cidade filtram
 * pela empresa do lead.
 */
@Repository
public class DashboardQuery {

    /** Filtros. Periodo semiaberto: {@code [from, to)}. */
    public record Filters(UUID organizationId, Instant from, Instant to, String segment, String city) {}

    public record Summary(
            Map<String, Long> leadsByStatus,
            long activeLeads,
            long companiesAdded,
            long contactsRegistered,
            long openProposals,
            long dealsWon,
            BigDecimal wonValue,
            long openTasks,
            long overdueTasks) {}

    public record Activity(
            String type, UUID leadId, String companyName, Instant occurredAt, String actorName, String fromStatus,
            String toStatus, String interactionType, String channel) {}

    /** Empresa do lead dentro dos filtros de segmento e cidade. */
    /** Fora do funil ativo: encerrados e bloqueados. */
    private static final EnumSet<LeadStatus> INACTIVE = EnumSet.of(LeadStatus.WON, LeadStatus.LOST, LeadStatus.DO_NOT_CONTACT);

    private static final String COMPANY_FILTER = """
            (cast(:segment as text) is null or c.segment = :segment)
            and (cast(:city as text) is null or lower(c.city) = lower(:city))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    DashboardQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Summary summary(Filters filters, Instant now) {
        MapSqlParameterSource params = params(filters).addValue("now", Timestamp.from(now));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("""
                select l.status, count(*) as total
                  from lead l join company c on c.id = l.company_id
                 where l.organization_id = :organizationId and %s
                 group by l.status
                """.formatted(COMPANY_FILTER), params,
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
                """.formatted(COMPANY_FILTER), params);

        // Contato registrado: abordagem de saida que o operador confirmou ter feito.
        long contacts = count("""
                select count(*) from interaction i
                  join lead l on l.id = i.lead_id join company c on c.id = l.company_id
                 where i.organization_id = :organizationId
                   and i.interaction_type = 'CONTACT' and i.direction = 'OUTBOUND'
                   and i.occurred_at >= :from and i.occurred_at < :to and %s
                """.formatted(COMPANY_FILTER), params);

        Map<String, Object> won = jdbc.queryForMap("""
                select count(*) as total, coalesce(sum(d.estimated_value), 0) as value
                  from lead l join company c on c.id = l.company_id
                  left join deal d on d.organization_id = l.organization_id and d.lead_id = l.id
                 where l.organization_id = :organizationId and l.status = 'WON'
                   and l.closed_at >= :from and l.closed_at < :to and %s
                """.formatted(COMPANY_FILTER), params);

        Map<String, Object> tasks = jdbc.queryForMap("""
                select count(*) as open, count(*) filter (where t.due_at < :now) as overdue
                  from task t join lead l on l.id = t.lead_id join company c on c.id = l.company_id
                 where t.organization_id = :organizationId and t.status = 'OPEN' and %s
                """.formatted(COMPANY_FILTER), params);

        return new Summary(
                byStatus,
                active,
                companies,
                contacts,
                byStatus.getOrDefault("PROPOSAL_SENT", 0L),
                ((Number) won.get("total")).longValue(),
                (BigDecimal) won.get("value"),
                ((Number) tasks.get("open")).longValue(),
                ((Number) tasks.get("overdue")).longValue());
    }

    /** Atividade recente: mudancas de status e interacoes, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public Page<Activity> activity(Filters filters, Pageable pageable) {
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
                """.formatted(COMPANY_FILTER);
        MapSqlParameterSource params = params(filters)
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

    private static MapSqlParameterSource params(Filters filters) {
        return new MapSqlParameterSource()
                .addValue("organizationId", filters.organizationId())
                .addValue("from", Timestamp.from(filters.from()))
                .addValue("to", Timestamp.from(filters.to()))
                .addValue("segment", Texts.blankToNull(filters.segment()))
                .addValue("city", Texts.blankToNull(filters.city()));
    }
}
