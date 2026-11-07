package com.jhcomp.prospect.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.TimeZone;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Validates migration V1 against a real PostgreSQL (doc 10 §2).
 *
 * <p>It checks both Flyway's record and the migration's concrete effect,
 * because a successful history does not prove the object it created works.
 */
@DisplayName("Migration V1 (baseline)")
class BaselineMigrationTest extends PostgresIntegrationTest {

    private final JdbcTemplate jdbc;

    BaselineMigrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("was applied successfully and with no checksum mismatch")
    void baselineApplied() {
        var applied = jdbc.queryForList(
                "select version, description, success from flyway_schema_history order by installed_rank");

        assertThat(applied)
                .describedAs("historico do Flyway")
                .isNotEmpty();
        assertThat(applied).allSatisfy(row ->
                assertThat(row.get("success")).describedAs("migration %s", row.get("version")).isEqualTo(true));
        assertThat(applied.getFirst()).containsEntry("version", "1");
    }

    @Test
    @DisplayName("installs unaccent, used by the shared normalisation (D10)")
    void unaccentIsInstalled() {
        var installed = jdbc.queryForObject(
                "select exists(select 1 from pg_extension where extname = 'unaccent')", Boolean.class);

        assertThat(installed).isTrue();
    }

    @Test
    @DisplayName("unaccent normalises the accents used in company names")
    void unaccentNormalizesBusinessNames() {
        // A real case from doc 10 §4: a name with accents and excess
        // whitespace, a tab included. "\\s" in Java: with a single backslash,
        // "\s" is a literal space (Java 15+) and the SQL regex would let the
        // tab through.
        var normalized = jdbc.queryForObject(
                "select lower(unaccent(trim(regexp_replace(?, '\\s+', ' ', 'g'))))",
                String.class,
                "  Salão \t Beleza & Estética  ");

        assertThat(normalized).isEqualTo("salao beleza & estetica");
    }

    @Test
    @DisplayName("timestamptz preserves the absolute instant, as RNF-009 requires")
    void timestamptzPreservesInstant() {
        // The server's TimeZone setting is not checked here: the JDBC driver
        // sends it in the connection's startup packet, so both setting and
        // reset_val in pg_settings end up reflecting the JVM's zone rather
        // than the database's. What RNF-009 actually requires is that the
        // instant survives the round trip without shifting, which is what this
        // test proves.
        var expected = Instant.parse("2026-01-15T12:00:00Z");

        // The instant goes as a literal, not a parameter: PostgreSQL does not
        // infer the type of a bare parameter inside a cast.
        var roundTrip = jdbc.queryForObject(
                "select cast('2026-01-15T12:00:00Z' as timestamptz)", Instant.class);

        assertThat(roundTrip).isEqualTo(expected);

        // The same instant written at another offset must read back equal.
        var sameInstantOtherOffset = jdbc.queryForObject(
                "select cast('2026-01-15T09:00:00-03:00' as timestamptz)", Instant.class);

        assertThat(sameInstantOtherOffset).isEqualTo(expected);
    }

    @Test
    @DisplayName("the test JVM runs in UTC, so the tests are deterministic")
    void testJvmRunsInUtc() {
        // Pinned through surefire's argLine in pom.xml. Without it, a time
        // zone bug would pass on the developer's machine and fail in CI, or the
        // other way round.
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }
}
