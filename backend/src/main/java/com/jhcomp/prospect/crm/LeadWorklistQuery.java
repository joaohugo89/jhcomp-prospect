package com.jhcomp.prospect.crm;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyOriginSql;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.shared.normalization.ContactNormalizer;
import com.jhcomp.prospect.shared.persistence.LikePatterns;
import com.jhcomp.prospect.shared.text.Texts;

/**
 * Fila de trabalho de leads (RF-012, doc 08 §6): cada linha ja diz o que fazer
 * com o lead, com responsavel, valor, proxima tarefa e tempo na etapa.
 *
 * <p>Leitura em SQL no modulo de CRM, e nao na listagem do modulo de lead: os
 * campos vem de deal, tarefa e historico, e o modulo de lead nao depende do de
 * CRM. Ordenar por valor ou por prazo da proxima tarefa exige o banco.
 *
 * <p>As regras de "parado" e "sem tarefa" sao as do Kanban e do painel de
 * atencao (D30), e a origem e a da D29: o numero do dashboard bate com a lista
 * que ele abre.
 */
@Repository
public class LeadWorklistQuery {

    /** Mesmo limite do Kanban e do painel de atencao. */
    static final int STALE_DAYS = 7;

    /** Recortes de um clique, os mesmos do painel de atencao. */
    public enum Focus { OVERDUE, STALE, WITHOUT_TASK }

    public enum SortField { COMPANY, SCORE, CREATED, UPDATED, STAGE, VALUE, NEXT_TASK }

    /**
     * Criterios; nulos nao filtram. {@code noSegment} so as empresas sem
     * segmento; {@code leadId} um lead so (resumo do detalhe e da previa).
     */
    public record Criteria(
            UUID leadId,
            String search,
            LeadStatus status,
            LeadPriority priority,
            String city,
            String segment,
            boolean noSegment,
            SourceType source,
            UUID assignee,
            boolean unassigned,
            Integer scoreMin,
            Integer scoreMax,
            Focus focus,
            SortField sort,
            boolean ascending) {}

    public record Item(
            UUID leadId,
            UUID companyId,
            String companyName,
            String segment,
            String city,
            String state,
            String status,
            String priority,
            Integer score,
            UUID assignedUserId,
            String assigneeName,
            BigDecimal estimatedValue,
            String nextTaskTitle,
            Instant nextTaskDueAt,
            Instant stageEnteredAt,
            Instant createdAt,
            Instant updatedAt) {}

    /**
     * Expressao de ordenacao por campo. Nulos sempre por ultimo: lead sem
     * valor nao aparece "primeiro" ao ordenar pelo maior valor.
     */
    private static final Map<SortField, String> ORDER = Map.of(
            SortField.COMPANY, "c.normalized_name",
            SortField.SCORE, "l.score",
            SortField.CREATED, "l.created_at",
            SortField.UPDATED, "l.updated_at",
            SortField.STAGE, "entry.changed_at",
            SortField.VALUE, "d.estimated_value",
            SortField.NEXT_TASK, "nt.due_at");

    private static final String IN_PROGRESS =
            "l.status in ('QUALIFIED', 'CONTACT_APPROVED', 'CONTACTED', 'IN_CONVERSATION', 'PROPOSAL_SENT')";

    private final NamedParameterJdbcTemplate jdbc;

    LeadWorklistQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<Item> find(UUID organizationId, Criteria criteria, Instant now, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("now", Timestamp.from(now))
                .addValue("staleBefore", Timestamp.from(now.minus(Duration.ofDays(STALE_DAYS))))
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        List<String> where = new ArrayList<>();
        where.add("l.organization_id = :organizationId");
        if (criteria.leadId() != null) {
            where.add("l.id = :leadId");
            params.addValue("leadId", criteria.leadId());
        }
        ContactNormalizer.name(criteria.search()).ifPresent(term -> {
            where.add("c.normalized_name like :search escape '\\'");
            params.addValue("search", LikePatterns.contains(term));
        });
        if (criteria.status() != null) {
            where.add("l.status = :status");
            params.addValue("status", criteria.status().name());
        }
        if (criteria.priority() != null) {
            where.add("l.priority = :priority");
            params.addValue("priority", criteria.priority().name());
        }
        String city = Texts.blankToNull(criteria.city());
        if (city != null) {
            where.add("lower(c.city) = lower(btrim(:city))");
            params.addValue("city", city);
        }
        String segment = Texts.blankToNull(criteria.segment());
        if (criteria.noSegment()) {
            where.add(CompanyOriginSql.WITHOUT_SEGMENT);
        } else if (segment != null) {
            where.add(CompanyOriginSql.SEGMENT_EQUALS);
            params.addValue("segment", segment);
        }
        if (criteria.source() != null) {
            where.add(CompanyOriginSql.ORIGIN + " = :source");
            params.addValue("source", criteria.source().name());
        }
        if (criteria.unassigned()) {
            where.add("l.assigned_user_id is null");
        } else if (criteria.assignee() != null) {
            where.add("l.assigned_user_id = :assignee");
            params.addValue("assignee", criteria.assignee());
        }
        // Faixa de score nao inclui lead sem score: sem analise nao e score baixo (D7).
        if (criteria.scoreMin() != null) {
            where.add("l.score >= :scoreMin");
            params.addValue("scoreMin", criteria.scoreMin());
        }
        if (criteria.scoreMax() != null) {
            where.add("l.score <= :scoreMax");
            params.addValue("scoreMax", criteria.scoreMax());
        }
        if (criteria.focus() != null) {
            where.add(switch (criteria.focus()) {
                case OVERDUE -> "l.status <> 'DO_NOT_CONTACT' and nt.due_at < :now";
                case STALE -> IN_PROGRESS + " and entry.changed_at <= :staleBefore";
                case WITHOUT_TASK -> IN_PROGRESS + " and nt.lead_id is null";
            });
        }

        SortField sort = criteria.sort() == null ? SortField.UPDATED : criteria.sort();
        String order = "%s %s nulls last, l.id".formatted(ORDER.get(sort), criteria.ascending() ? "asc" : "desc");

        String sql = """
                select l.id as lead_id, c.id as company_id, c.name as company_name, c.segment, c.city, c.state,
                       l.status, l.priority, l.score, l.assigned_user_id, u.display_name as assignee_name,
                       d.estimated_value, nt.title as next_task_title, nt.due_at as next_task_due_at,
                       entry.changed_at as stage_entered_at, l.created_at, l.updated_at,
                       count(*) over () as total
                  from lead l
                  join company c on c.id = l.company_id
                  left join deal d on d.organization_id = l.organization_id and d.lead_id = l.id
                  left join app_user u on u.id = l.assigned_user_id
                  -- Entrada na etapa atual: a ultima mudanca pela ordem de gravacao.
                  left join lateral (
                      select s.changed_at from lead_status_change s
                       where s.organization_id = l.organization_id and s.lead_id = l.id
                       order by s.recorded_seq desc limit 1
                  ) entry on true
                  -- Proxima tarefa aberta: a de prazo mais proximo; sem prazo por ultimo.
                  left join lateral (
                      select t.lead_id, t.title, t.due_at from task t
                       where t.organization_id = l.organization_id and t.lead_id = l.id and t.status = 'OPEN'
                       order by t.due_at nulls last, t.created_at limit 1
                  ) nt on true
                 where %s
                 order by %s
                 limit :limit offset :offset
                """.formatted(String.join("\n   and ", where), order);

        long[] total = {0};
        List<Item> items = jdbc.query(sql, params, (row, rowNumber) -> {
            total[0] = row.getLong("total");
            return new Item(
                    row.getObject("lead_id", UUID.class),
                    row.getObject("company_id", UUID.class),
                    row.getString("company_name"),
                    row.getString("segment"),
                    row.getString("city"),
                    row.getString("state"),
                    row.getString("status"),
                    row.getString("priority"),
                    row.getObject("score", Integer.class),
                    row.getObject("assigned_user_id", UUID.class),
                    row.getString("assignee_name"),
                    row.getBigDecimal("estimated_value"),
                    row.getString("next_task_title"),
                    instant(row.getTimestamp("next_task_due_at")),
                    instant(row.getTimestamp("stage_entered_at")),
                    instant(row.getTimestamp("created_at")),
                    instant(row.getTimestamp("updated_at")));
        });
        // Pagina alem do fim nao traz linha, e com ela o total: conta a parte.
        if (items.isEmpty() && pageable.getOffset() > 0) {
            total[0] = count(where, params);
        }
        return new PageImpl<>(items, pageable, total[0]);
    }

    private long count(List<String> where, MapSqlParameterSource params) {
        Long value = jdbc.queryForObject("""
                select count(*)
                  from lead l
                  join company c on c.id = l.company_id
                  left join lateral (
                      select s.changed_at from lead_status_change s
                       where s.organization_id = l.organization_id and s.lead_id = l.id
                       order by s.recorded_seq desc limit 1
                  ) entry on true
                  left join lateral (
                      select t.lead_id, t.due_at from task t
                       where t.organization_id = l.organization_id and t.lead_id = l.id and t.status = 'OPEN'
                       order by t.due_at nulls last, t.created_at limit 1
                  ) nt on true
                 where %s
                """.formatted(String.join("\n   and ", where)), params, Long.class);
        return value == null ? 0 : value;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
