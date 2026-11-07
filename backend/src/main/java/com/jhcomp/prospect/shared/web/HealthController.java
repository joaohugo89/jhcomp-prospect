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
 * The availability check (doc 03 §11, D27).
 *
 * <p>It verifies the database connection: without it the application serves
 * nothing, and a health reporting "UP" with the database down would fool both
 * the external monitor and the container's health check. It answers 503 in
 * that case.
 *
 * <p>It exposes no dependency version, no file path and no infrastructure
 * detail: the response is deliberately minimal, and the reason for a failure
 * goes to the log only.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    /** Seconds for the database to answer before health declares a failure. */
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

    /** A health check response. {@code checkedAt} is always UTC (RNF-009). */
    public record HealthResponse(String status, String version, Instant checkedAt) {}
}
