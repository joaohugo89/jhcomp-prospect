package com.jhcomp.prospect.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Limite de tentativas de login (doc 07 §4). Unitario, sem Spring. */
@DisplayName("LoginAttemptLimiter")
class LoginAttemptLimiterTest {

    private static final String EMAIL = "operador@exemplo.test";
    private static final String IP = "203.0.113.10";

    /** Relogio controlavel: testar janela de tempo com sleep seria lento e instavel. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-15T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Test
    @DisplayName("nao bloqueia antes do limite")
    void allowsUpToLimit() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES - 1; attempt++) {
            limiter.recordFailure(EMAIL, IP);
            assertThat(limiter.isBlocked(EMAIL, IP)).isFalse();
        }
    }

    @Test
    @DisplayName("bloqueia ao atingir o limite de falhas")
    void blocksAtLimit() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.recordFailure(EMAIL, IP);
        }

        assertThat(limiter.isBlocked(EMAIL, IP)).isTrue();
    }

    @Test
    @DisplayName("libera apos a janela expirar")
    void releasesAfterWindow() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.recordFailure(EMAIL, IP);
        }
        assertThat(limiter.isBlocked(EMAIL, IP)).isTrue();

        clock.advance(LoginAttemptLimiter.WINDOW.plusSeconds(1));

        assertThat(limiter.isBlocked(EMAIL, IP)).isFalse();
    }

    @Test
    @DisplayName("login bem-sucedido zera o contador")
    void successResetsCounter() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES - 1; attempt++) {
            limiter.recordFailure(EMAIL, IP);
        }
        limiter.recordSuccess(EMAIL, IP);
        limiter.recordFailure(EMAIL, IP);

        assertThat(limiter.isBlocked(EMAIL, IP)).isFalse();
    }

    @Test
    @DisplayName("bloqueio de um IP nao atinge outro IP do mesmo e-mail")
    void blockIsScopedByIp() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.recordFailure(EMAIL, IP);
        }

        // Caso o bloqueio fosse apenas por e-mail, um atacante travaria a conta
        // de um terceiro de proposito.
        assertThat(limiter.isBlocked(EMAIL, "198.51.100.7")).isFalse();
    }

    @Test
    @DisplayName("bloqueio de um e-mail nao atinge outro e-mail do mesmo IP")
    void blockIsScopedByEmail() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.recordFailure(EMAIL, IP);
        }

        assertThat(limiter.isBlocked("outro@exemplo.test", IP)).isFalse();
    }
}
