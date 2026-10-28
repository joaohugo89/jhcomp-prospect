package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Historico consolidado do lead (RF-013): mudancas de status e origens, em
 * ordem cronologica decrescente.
 *
 * <p>SQL direto porque une duas tabelas numa unica paginacao; com duas
 * consultas paginadas separadas, a ordem entre elas so seria correta pagina a
 * pagina. Ambos os lados filtram por organizacao.
 *
 * <p>Empate de instante e desempatado pela ordem de gravacao
 * ({@code recorded_seq}, V6), e nao pelo id aleatorio: o relogio pode dar o
 * mesmo instante a eventos gravados em sequencia.
 */
@Repository
public class LeadTimelineQuery {

    /** Tipo do evento no historico. */
    public enum EntryType {
        STATUS_CHANGE,
        SOURCE
    }

    /** Evento do historico. Campos de status ou de origem conforme o tipo. */
    public record Entry(
            EntryType type,
            UUID id,
            Instant occurredAt,
            String actorName,
            String fromStatus,
            String toStatus,
            String reason,
            String sourceType,
            String sourceName,
            String sourceUrl,
            String sourceReference) {}

    private static final String EVENTS = """
            select 'STATUS_CHANGE' as entry_type, c.id, c.changed_at as occurred_at, c.recorded_seq,
                   u.display_name as actor_name,
                   c.from_status, c.to_status, c.reason,
                   null as source_type, null as source_name, null as source_url, null as source_reference
              from lead_status_change c
              join app_user u on u.id = c.changed_by_user_id
             where c.organization_id = :organizationId and c.lead_id = :leadId
            union all
            select 'SOURCE', s.id, s.collected_at, s.recorded_seq, u.display_name,
                   null, null, null,
                   s.source_type, s.source_name, s.source_url, s.source_reference
              from lead_source s
              left join app_user u on u.id = s.collected_by_user_id
             where s.organization_id = :organizationId and s.lead_id = :leadId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    LeadTimelineQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<Entry> find(UUID organizationId, UUID leadId, Pageable pageable) {
        var params = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("leadId", leadId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        List<Entry> entries = jdbc.query(
                "select * from (" + EVENTS + ") events order by occurred_at desc, recorded_seq desc limit :limit offset :offset",
                params,
                LeadTimelineQuery::toEntry);

        Long total = jdbc.queryForObject("select count(*) from (" + EVENTS + ") events", params, Long.class);

        return new PageImpl<>(entries, pageable, total == null ? 0 : total);
    }

    private static Entry toEntry(ResultSet row, int rowNumber) throws SQLException {
        return new Entry(
                EntryType.valueOf(row.getString("entry_type")),
                row.getObject("id", UUID.class),
                row.getTimestamp("occurred_at").toInstant(),
                row.getString("actor_name"),
                row.getString("from_status"),
                row.getString("to_status"),
                row.getString("reason"),
                row.getString("source_type"),
                row.getString("source_name"),
                row.getString("source_url"),
                row.getString("source_reference"));
    }
}
