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
 * The instant of a password change is the same in the session and in the
 * database (D28).
 *
 * <p>A session ends when the password changed after it. If the database kept
 * an instant slightly later than the session's, whoever had just changed
 * their password would lose their own session.
 */
@DisplayName("The instant of a password change")
class CredentialsInstantTest extends PostgresIntegrationTest {

    /** A sub-microsecond fraction that PostgreSQL rounds up. */
    private static final Instant WITH_NANOS = Instant.parse("2026-11-06T12:00:00.000000900Z");

    private final JdbcTemplate jdbc;

    CredentialsInstantTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("PostgreSQL rounds a sub-microsecond fraction up")
    void postgresRoundsSubMicroseconds() {
        Instant stored = jdbc.queryForObject("select ?::timestamptz", Instant.class, WITH_NANOS.toString());

        assertThat(stored).isAfter(WITH_NANOS);
    }

    @Test
    @DisplayName("the entity truncates to the microsecond, and the database returns exactly the same instant")
    void entityStoresWhatTheDatabaseKeeps() {
        UserEntity user = new UserEntity(UUID.randomUUID(), "x@example.test", "{noop}x", "X", UserStatus.ACTIVE,
                false, WITH_NANOS);
        user.replacePassword("{noop}y", false, WITH_NANOS);

        Instant stored = jdbc.queryForObject(
                "select ?::timestamptz", Instant.class, user.getCredentialsChangedAt().toString());

        assertThat(user.getCredentialsChangedAt()).isBeforeOrEqualTo(WITH_NANOS).isEqualTo(stored);
    }
}
