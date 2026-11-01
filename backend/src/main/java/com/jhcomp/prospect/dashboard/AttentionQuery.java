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
 * "Precisa de atencao": o que o operador deve fazer agora (doc 08 §4).
 *
 * <p>Quatro secoes, cada uma com o total e os {@code limit} mais urgentes; o
 * resto se acha pelo lead. Situacao de agora, sem periodo: tarefa atrasada nao
 * deixa de estar atrasada porque o filtro e dos ultimos 7 dias. Lead em
 * {@code DO_NOT_CONTACT} fica de fora de todas: o painel nunca empurra uma
 * abordagem a quem pediu para nao ser contatado (regra 4).
 */
@Repository
public class AttentionQuery {

    /** Dias numa etapa em andamento a partir dos quais o lead esta parado (o mesmo do Kanban). */
    static final int STALE_DAYS = 7;

    /** Dias sem toque numa proposta: o dinheiro mais perto de esfriar. */
    static final int QUIET_PROPOSAL_DAYS = 7;

    static final int MAX_LIMIT = 20;

    public record Item(UUID leadId, String companyName, String status, Instant at, String detail, boolean overdue) {}

    public record Section(long total, List<Item> items) {}

    public record Attention(Section tasks, Section staleLeads, Section quietProposals, Section withoutTask) {}

    /** Ultima mudanca de status de cada lead, pela ordem de gravacao: a entrada na etapa atual. */
    private static final String LATEST_CHANGE = """
            latest as (
                select distinct on (s.lead_id) s.lead_id, s.changed_at
                  from lead_status_change s
                 where s.organization_id = :organizationId
                 order by s.lead_id, s.recorded_seq desc
            )""";

    /** Etapas em andamento; {@code NEW} e triagem, ainda sem compromisso de proximo passo. */
    private static final String IN_PROGRESS =
            "l.status in ('QUALIFIED', 'CONTACT_APPROVED', 'CONTACTED', 'IN_CONVERSATION', 'PROPOSAL_SENT')";

    private final NamedParameterJdbcTemplate jdbc;

    AttentionQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param endOfToday fim do dia de hoje no fuso do operador: tarefa que vence
     *     hoje entra junto com as atrasadas
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

        // Ultimo toque: a interacao mais recente ou, sem ela, a chegada na proposta.
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
