package com.jhcomp.prospect.crm;

import static com.jhcomp.prospect.shared.persistence.SqlValues.instant;

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
 * The lead work queue (RF-012, doc 08 §6): each row already says what to do
 * with the lead, with owner, value, next task and time in stage.
 *
 * <p>Read in SQL in the CRM module rather than in the lead module's listing:
 * the fields come from deal, task and history, and the lead module does not
 * depend on the CRM. Sorting by value, or by the next task's due date,
 * requires the database.
 *
 * <p>The "stalled" and "no task" rules are the Kanban's and the attention
 * panel's (D30), and the origin is D29's: the dashboard's number matches the
 * list it opens.
 */
@Repository
public class LeadWorklistQuery {

    /** The same threshold as the Kanban and the attention panel. */
    static final int STALE_DAYS = 7;

    /** One-click slices, the same ones as the attention panel. */
    public enum Focus { OVERDUE, STALE, WITHOUT_TASK }

    public enum SortField { COMPANY, SCORE, CREATED, UPDATED, STAGE, VALUE, NEXT_TASK }

    /**
     * Criteria; nulls filter nothing. {@code noSegment} keeps only companies
     * with no segment; {@code leadId} narrows to one lead (the detail's and
     * the preview's summary).
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
     * The sort expression per field. Nulls always last: a lead with no value
     * must not come "first" when sorting by highest value.
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
        // A score range excludes leads with no score: no analysis is not a low score (D7).
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
        // A page past the end returns no row, and with it no total: count separately.
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
}
