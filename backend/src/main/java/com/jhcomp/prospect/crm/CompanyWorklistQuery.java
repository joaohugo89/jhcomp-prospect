package com.jhcomp.prospect.crm;

import static com.jhcomp.prospect.shared.persistence.SqlValues.instant;

import java.math.BigDecimal;
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
import com.jhcomp.prospect.shared.normalization.ContactNormalizer;
import com.jhcomp.prospect.shared.persistence.LikePatterns;
import com.jhcomp.prospect.shared.text.Texts;

/**
 * The company list as a tool (doc 08 §5, D32): each row says whether the
 * company has a lead, how to reach it, where it came from, when there was
 * activity, and whether it looks like a duplicate.
 *
 * <p>In the CRM module for the same reason as the lead queue: the fields come
 * from lead, interaction and history, and the company module does not depend
 * on them.
 */
@Repository
public class CompanyWorklistQuery {

    /** One-click slices. Without {@code ARCHIVED}, only active ones appear. */
    public enum Focus { WITHOUT_LEAD, WITHOUT_CONTACT, DUPLICATE, ARCHIVED }

    public enum SortField { NAME, CITY, CREATED, LAST_ACTIVITY }

    /** Criteria; nulls filter nothing. {@code companyId} narrows to one company (the summary). */
    public record Criteria(
            UUID companyId,
            String search,
            String segment,
            boolean noSegment,
            String city,
            SourceType source,
            Focus focus,
            SortField sort,
            boolean ascending) {}

    /** The company's most relevant lead: the open one, or the last closed one. */
    public record CurrentLead(UUID leadId, String status, boolean open, String assigneeName) {}

    public record Item(
            UUID companyId,
            String name,
            String tradeName,
            String segment,
            String city,
            String state,
            boolean hasPhone,
            boolean hasEmail,
            boolean hasWebsite,
            boolean hasInstagram,
            String origin,
            CurrentLead currentLead,
            int leadCount,
            Instant lastActivityAt,
            boolean possibleDuplicate,
            boolean archived,
            Instant createdAt) {}

    /** One commercial cycle of the company, newest first. */
    public record LeadCycle(
            UUID leadId,
            String status,
            String priority,
            Integer score,
            String assigneeName,
            BigDecimal estimatedValue,
            Instant createdAt,
            Instant closedAt) {}

    private static final Map<SortField, String> ORDER = Map.of(
            SortField.NAME, "c.normalized_name",
            SortField.CITY, "lower(c.city)",
            SortField.CREATED, "c.created_at",
            SortField.LAST_ACTIVITY, "act.at");

    /**
     * Another active company with the same domain, phone, or name in the same
     * city and state: the signals of RF-014. An archived one does not count,
     * or the source of a merge would flag the target forever.
     */
    private static final String POSSIBLE_DUPLICATE = """
            exists (select 1 from company o
                     where o.organization_id = c.organization_id and o.id <> c.id and o.archived_at is null
                       and ((c.normalized_domain is not null and o.normalized_domain = c.normalized_domain)
                            or (c.normalized_phone is not null and o.normalized_phone = c.normalized_phone)
                            or (o.normalized_name = c.normalized_name and lower(o.city) = lower(c.city)
                                and o.state = c.state)))""";

    private static final String WITHOUT_CONTACT = """
            (c.phone is null and c.business_email is null and c.website_url is null and c.instagram_url is null)""";

    private static final String FROM = """
              from company c
              -- Lead atual: o aberto; sem ele, o mais recente.
              left join lateral (
                  select l.id, l.status, l.closed_at, u.display_name from lead l
                    left join app_user u on u.id = l.assigned_user_id
                   where l.organization_id = c.organization_id and l.company_id = c.id
                   order by (l.closed_at is null) desc, l.created_at desc, l.id limit 1
              ) cl on true
              -- Ultima atividade: interacao ou mudanca de etapa em qualquer ciclo.
              left join lateral (
                  select greatest(
                      (select max(i.occurred_at) from interaction i
                         join lead l on l.organization_id = i.organization_id and l.id = i.lead_id
                        where l.organization_id = c.organization_id and l.company_id = c.id),
                      (select max(s.changed_at) from lead_status_change s
                         join lead l on l.organization_id = s.organization_id and l.id = s.lead_id
                        where l.organization_id = c.organization_id and l.company_id = c.id)) as at
              ) act on true
            """;

    private final NamedParameterJdbcTemplate jdbc;

    CompanyWorklistQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Page<Item> find(UUID organizationId, Criteria criteria, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        List<String> where = new ArrayList<>();
        where.add("c.organization_id = :organizationId");
        if (criteria.companyId() != null) {
            where.add("c.id = :companyId");
            params.addValue("companyId", criteria.companyId());
        } else {
            where.add(criteria.focus() == Focus.ARCHIVED ? "c.archived_at is not null" : "c.archived_at is null");
        }
        ContactNormalizer.name(criteria.search()).ifPresent(term -> {
            where.add("c.normalized_name like :search escape '\\'");
            params.addValue("search", LikePatterns.contains(term));
        });
        String segment = Texts.blankToNull(criteria.segment());
        if (criteria.noSegment()) {
            where.add(CompanyOriginSql.WITHOUT_SEGMENT);
        } else if (segment != null) {
            where.add(CompanyOriginSql.SEGMENT_EQUALS);
            params.addValue("segment", segment);
        }
        String city = Texts.blankToNull(criteria.city());
        if (city != null) {
            where.add("lower(c.city) = lower(btrim(:city))");
            params.addValue("city", city);
        }
        if (criteria.source() != null) {
            where.add(CompanyOriginSql.ORIGIN + " = :source");
            params.addValue("source", criteria.source().name());
        }
        if (criteria.focus() != null) {
            switch (criteria.focus()) {
                case WITHOUT_LEAD -> where.add("(cl.id is null or cl.closed_at is not null)");
                case WITHOUT_CONTACT -> where.add(WITHOUT_CONTACT);
                case DUPLICATE -> where.add(POSSIBLE_DUPLICATE);
                case ARCHIVED -> {
                    // Already applied above.
                }
            }
        }

        SortField sort = criteria.sort() == null ? SortField.CREATED : criteria.sort();
        String order = "%s %s nulls last, c.id".formatted(ORDER.get(sort), criteria.ascending() ? "asc" : "desc");
        String conditions = String.join("\n   and ", where);

        String sql = """
                select c.id, c.name, c.trade_name, c.segment, c.city, c.state,
                       c.phone is not null as has_phone, c.business_email is not null as has_email,
                       c.website_url is not null as has_website, c.instagram_url is not null as has_instagram,
                       %s as origin,
                       cl.id as lead_id, cl.status as lead_status, cl.closed_at is null as lead_open,
                       cl.display_name as assignee_name,
                       (select count(*) from lead l where l.organization_id = c.organization_id and l.company_id = c.id)
                           as lead_count,
                       act.at as last_activity_at,
                       %s as possible_duplicate,
                       c.archived_at is not null as archived, c.created_at,
                       count(*) over () as total
                %s
                 where %s
                 order by %s
                 limit :limit offset :offset
                """.formatted(CompanyOriginSql.ORIGIN, POSSIBLE_DUPLICATE, FROM, conditions, order);

        long[] total = {0};
        List<Item> items = jdbc.query(sql, params, (row, rowNumber) -> {
            total[0] = row.getLong("total");
            UUID leadId = row.getObject("lead_id", UUID.class);
            return new Item(
                    row.getObject("id", UUID.class),
                    row.getString("name"),
                    row.getString("trade_name"),
                    row.getString("segment"),
                    row.getString("city"),
                    row.getString("state"),
                    row.getBoolean("has_phone"),
                    row.getBoolean("has_email"),
                    row.getBoolean("has_website"),
                    row.getBoolean("has_instagram"),
                    row.getString("origin"),
                    leadId == null ? null : new CurrentLead(
                            leadId, row.getString("lead_status"), row.getBoolean("lead_open"),
                            row.getString("assignee_name")),
                    row.getInt("lead_count"),
                    instant(row.getTimestamp("last_activity_at")),
                    row.getBoolean("possible_duplicate"),
                    row.getBoolean("archived"),
                    instant(row.getTimestamp("created_at")));
        });
        if (items.isEmpty() && pageable.getOffset() > 0) {
            Long count = jdbc.queryForObject("select count(*) %s where %s".formatted(FROM, conditions), params,
                    Long.class);
            total[0] = count == null ? 0 : count;
        }
        return new PageImpl<>(items, pageable, total[0]);
    }

    /** The company's commercial cycles, newest first. */
    @Transactional(readOnly = true)
    public Page<LeadCycle> leads(UUID organizationId, UUID companyId, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource("organizationId", organizationId)
                .addValue("companyId", companyId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        long[] total = {0};
        List<LeadCycle> cycles = jdbc.query("""
                select l.id, l.status, l.priority, l.score, u.display_name, d.estimated_value, l.created_at,
                       l.closed_at, count(*) over () as total
                  from lead l
                  left join app_user u on u.id = l.assigned_user_id
                  left join deal d on d.organization_id = l.organization_id and d.lead_id = l.id
                 where l.organization_id = :organizationId and l.company_id = :companyId
                 order by l.created_at desc, l.id
                 limit :limit offset :offset
                """, params, (row, rowNumber) -> {
                    total[0] = row.getLong("total");
                    return new LeadCycle(
                            row.getObject("id", UUID.class),
                            row.getString("status"),
                            row.getString("priority"),
                            row.getObject("score", Integer.class),
                            row.getString("display_name"),
                            row.getBigDecimal("estimated_value"),
                            instant(row.getTimestamp("created_at")),
                            instant(row.getTimestamp("closed_at")));
                });
        if (cycles.isEmpty() && pageable.getOffset() > 0) {
            Long count = jdbc.queryForObject(
                    "select count(*) from lead where organization_id = :organizationId and company_id = :companyId",
                    params, Long.class);
            total[0] = count == null ? 0 : count;
        }
        return new PageImpl<>(cycles, pageable, total[0]);
    }
}
