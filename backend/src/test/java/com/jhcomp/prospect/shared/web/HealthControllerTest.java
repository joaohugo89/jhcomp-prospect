package com.jhcomp.prospect.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Teste unitario: rapido e sem rede ou servico externo (doc 10 §2).
 */
@DisplayName("HealthController")
class HealthControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");

    private final DataSource dataSource = mock(DataSource.class);

    private HealthController controller() {
        return new HealthController("0.1.0-TEST", dataSource, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("reporta UP com a versao da aplicacao quando o banco responde")
    void reportsUp() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);

        var response = controller().health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
        assertThat(response.getBody().version()).isEqualTo("0.1.0-TEST");
        assertThat(response.getBody().checkedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("reporta DOWN com 503 quando o banco nao responde, sem detalhe na resposta")
    void reportsDownWithoutDatabase() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLTransientConnectionException("pool exhausted at db:5432"));

        var response = controller().health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DOWN");
        assertThat(response.getBody().toString()).doesNotContain("5432");
    }

    @Test
    @DisplayName("conexao invalida tambem e DOWN")
    void invalidConnectionIsDown() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(false);
        when(dataSource.getConnection()).thenReturn(connection);

        assertThat(controller().health().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
