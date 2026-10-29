package com.jhcomp.prospect.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste unitario: rapido e sem rede ou servico externo (doc 10 §2).
 */
@DisplayName("HealthController")
class HealthControllerTest {

    @Test
    @DisplayName("reporta UP com a versao da aplicacao")
    void reportsUp() {
        Instant now = Instant.parse("2026-01-15T12:00:00Z");
        var response = new HealthController("0.1.0-TEST", Clock.fixed(now, ZoneOffset.UTC)).health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.version()).isEqualTo("0.1.0-TEST");
        assertThat(response.checkedAt()).isEqualTo(now);
    }
}
