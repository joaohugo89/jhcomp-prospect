package com.jhcomp.prospect.company.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Segmentos em uso na organizacao, com quantas empresas ativas em cada.
 *
 * <p>Segmento e texto livre: sem uma lista do que ja existe, cada cadastro
 * inventa a sua grafia e o filtro por segmento deixa de juntar o que e igual.
 * Grafias que so diferem em caixa ou espaco contam como um segmento so; o
 * rotulo mostrado e a grafia mais usada.
 */
@Repository
public class CompanySegmentsQuery {

    static final int MAX_SEGMENTS = 200;

    public record Segment(String name, long companies) {}

    public record Segments(List<Segment> segments, long withoutSegment) {}

    private final NamedParameterJdbcTemplate jdbc;

    CompanySegmentsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Segments segments(UUID organizationId) {
        var params = new MapSqlParameterSource("organizationId", organizationId).addValue("max", MAX_SEGMENTS);
        List<Segment> segments = jdbc.query("""
                select mode() within group (order by btrim(c.segment)) as name, count(*) as companies
                  from company c
                 where c.organization_id = :organizationId and c.archived_at is null
                   and not %s
                 group by lower(btrim(c.segment))
                 order by companies desc, name
                 limit :max
                """.formatted(CompanyOriginSql.WITHOUT_SEGMENT), params,
                (row, rowNumber) -> new Segment(row.getString("name"), row.getLong("companies")));
        Long without = jdbc.queryForObject("""
                select count(*) from company c
                 where c.organization_id = :organizationId and c.archived_at is null and %s
                """.formatted(CompanyOriginSql.WITHOUT_SEGMENT), params, Long.class);
        return new Segments(segments, without == null ? 0 : without);
    }
}
