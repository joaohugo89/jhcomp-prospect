package com.jhcomp.prospect.shared.web;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificacao de disponibilidade (doc 03 §11, D27).
 *
 * <p>Confere a conexao com o banco: sem ela a aplicacao nao atende nada, e um
 * health "UP" com o banco fora do ar enganaria o monitor externo e o health
 * check do container. Responde 503 nesse caso.
 *
 * <p>Nao expoe versao de dependencia, caminho de arquivo nem detalhe de
 * infraestrutura: a resposta e deliberadamente minima, e o motivo da falha
 * vai so para o log.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    /** Segundos para o banco responder antes de o health declarar falha. */
    private static final int DATABASE_TIMEOUT_SECONDS = 2;

    private final String applicationVersion;
    private final DataSource dataSource;
    private final Clock clock;

    HealthController(
            @Value("${spring.application.version:unknown}") String applicationVersion,
            DataSource dataSource,
            Clock clock) {
        this.applicationVersion = applicationVersion;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        boolean databaseUp = databaseIsUp();
        return ResponseEntity.status(databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse(databaseUp ? "UP" : "DOWN", applicationVersion, clock.instant()));
    }

    private boolean databaseIsUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DATABASE_TIMEOUT_SECONDS);
        } catch (SQLException exception) {
            log.warn("Health check: database unavailable ({})", exception.getClass().getSimpleName());
            return false;
        }
    }

    /** Resposta de health check. {@code checkedAt} e sempre UTC (RNF-009). */
    public record HealthResponse(String status, String version, Instant checkedAt) {}
}
