package com.jhcomp.prospect.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.identity.infrastructure.persistence.UserEntity;
import com.jhcomp.prospect.shared.PostgresIntegrationTest;

/**
 * Instante da troca de senha guardado na sessao e no banco e o mesmo (D28).
 *
 * <p>A sessao e encerrada quando a senha mudou depois dela. Se o banco
 * guardasse um instante ligeiramente posterior ao da sessao, a pessoa que
 * acabou de trocar a senha perderia a propria sessao.
 */
@DisplayName("Instante da troca de senha")
class CredentialsInstantTest extends PostgresIntegrationTest {

    /** Fracao abaixo do microssegundo que o PostgreSQL arredonda para cima. */
    private static final Instant WITH_NANOS = Instant.parse("2026-11-06T12:00:00.000000900Z");

    private final JdbcTemplate jdbc;

    CredentialsInstantTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("o PostgreSQL arredonda para cima a fracao abaixo do microssegundo")
    void postgresRoundsSubMicroseconds() {
        Instant stored = jdbc.queryForObject("select ?::timestamptz", Instant.class, WITH_NANOS.toString());

        assertThat(stored).isAfter(WITH_NANOS);
    }

    @Test
    @DisplayName("a entidade trunca para o microssegundo, e o banco devolve exatamente o mesmo instante")
    void entityStoresWhatTheDatabaseKeeps() {
        UserEntity user = new UserEntity(UUID.randomUUID(), "x@example.test", "{noop}x", "X", UserStatus.ACTIVE,
                false, WITH_NANOS);
        user.replacePassword("{noop}y", false, WITH_NANOS);

        Instant stored = jdbc.queryForObject(
                "select ?::timestamptz", Instant.class, user.getCredentialsChangedAt().toString());

        assertThat(user.getCredentialsChangedAt()).isBeforeOrEqualTo(WITH_NANOS).isEqualTo(stored);
    }
}
