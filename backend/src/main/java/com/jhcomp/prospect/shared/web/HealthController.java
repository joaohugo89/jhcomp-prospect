package com.jhcomp.prospect.shared.web;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificacao de disponibilidade (doc 03 §11).
 *
 * <p>Nao expoe versao de dependencia, caminho de arquivo nem detalhe de
 * infraestrutura: a resposta e deliberadamente minima.
 */
@RestController
public class HealthController {

    private final String applicationVersion;
    private final Clock clock;

    HealthController(@Value("${spring.application.version:unknown}") String applicationVersion, Clock clock) {
        this.applicationVersion = applicationVersion;
        this.clock = clock;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", applicationVersion, clock.instant());
    }

    /** Resposta de health check. {@code checkedAt} e sempre UTC (RNF-009). */
    public record HealthResponse(String status, String version, Instant checkedAt) {}
}
