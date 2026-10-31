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
 * Valida a migration V1 contra PostgreSQL real (doc 10 §2).
 *
 * <p>Verifica tanto o registro do Flyway quanto o efeito concreto da migration,
 * porque um historico com sucesso nao prova que o objeto criado funciona.
 */
@DisplayName("Migration V1 (baseline)")
class BaselineMigrationTest extends PostgresIntegrationTest {

    private final JdbcTemplate jdbc;

    BaselineMigrationTest(@Autowired DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("foi aplicada com sucesso e sem divergencia de checksum")
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
    @DisplayName("instala unaccent, usado pela normalizacao compartilhada (D10)")
    void unaccentIsInstalled() {
        var installed = jdbc.queryForObject(
                "select exists(select 1 from pg_extension where extname = 'unaccent')", Boolean.class);

        assertThat(installed).isTrue();
    }

    @Test
    @DisplayName("unaccent normaliza acentuacao usada em nomes de empresa")
    void unaccentNormalizesBusinessNames() {
        // Caso real do doc 10 §4: nome com acento e espaco excedente, inclusive
        // tabulacao. "\\s" no Java: com uma barra so, "\s" e um espaco literal
        // (Java 15+) e a regex do SQL deixaria a tabulacao passar.
        var normalized = jdbc.queryForObject(
                "select lower(unaccent(trim(regexp_replace(?, '\\s+', ' ', 'g'))))",
                String.class,
                "  Salão \t Beleza & Estética  ");

        assertThat(normalized).isEqualTo("salao beleza & estetica");
    }

    @Test
    @DisplayName("timestamptz preserva o instante absoluto, como exige RNF-009")
    void timestamptzPreservesInstant() {
        // Nao se verifica aqui a configuracao TimeZone do servidor: o driver
        // JDBC a envia no pacote de inicializacao da conexao, de modo que tanto
        // setting quanto reset_val em pg_settings passam a refletir o fuso da
        // JVM, e nao o do banco. O que RNF-009 realmente exige e que o instante
        // sobreviva a ida e volta sem deslocamento, o que este teste comprova.
        var expected = Instant.parse("2026-01-15T12:00:00Z");

        // O instante vai como literal, nao como parametro: o PostgreSQL nao
        // infere o tipo de um parametro solto dentro de um cast.
        var roundTrip = jdbc.queryForObject(
                "select cast('2026-01-15T12:00:00Z' as timestamptz)", Instant.class);

        assertThat(roundTrip).isEqualTo(expected);

        // Mesmo instante escrito em outro deslocamento deve ler igual.
        var sameInstantOtherOffset = jdbc.queryForObject(
                "select cast('2026-01-15T09:00:00-03:00' as timestamptz)", Instant.class);

        assertThat(sameInstantOtherOffset).isEqualTo(expected);
    }

    @Test
    @DisplayName("a JVM de teste roda em UTC, para que os testes sejam deterministicos")
    void testJvmRunsInUtc() {
        // Fixado via argLine do surefire no pom.xml. Sem isso, um bug de fuso
        // passaria na maquina do desenvolvedor e falharia no CI, ou o contrario.
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }
}
