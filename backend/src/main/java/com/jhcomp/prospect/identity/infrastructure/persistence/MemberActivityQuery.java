package com.jhcomp.prospect.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Ultimo acesso de cada membro, lido da auditoria de login (doc 07 §11): o
 * administrador ve quem nunca entrou e quem parou de usar, sem uma coluna a
 * mais que precisaria ser mantida a cada login.
 */
@Repository
public class MemberActivityQuery {

    private final NamedParameterJdbcTemplate jdbc;

    MemberActivityQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Login bem-sucedido mais recente por usuario, so nesta organizacao. Quem nunca entrou fica de fora. */
    public Map<UUID, Instant> lastLogins(UUID organizationId, Collection<UUID> userIds) {
        Map<UUID, Instant> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                select actor_user_id, max(occurred_at) as last_login from audit_event
                 where organization_id = :organizationId and action = 'LOGIN' and outcome = 'SUCCESS'
                   and actor_user_id in (:userIds)
                 group by actor_user_id
                """,
                new MapSqlParameterSource("organizationId", organizationId).addValue("userIds", userIds),
                row -> {
                    result.put(row.getObject("actor_user_id", UUID.class), row.getTimestamp("last_login").toInstant());
                });
        return result;
    }
}
