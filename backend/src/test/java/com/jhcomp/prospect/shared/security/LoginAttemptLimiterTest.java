package com.jhcomp.prospect.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Limite de tentativas de login (doc 07 §4). Unitario, sem Spring. */
@DisplayName("LoginAttemptLimiter")
class LoginAttemptLimiterTest {

    private static final String EMAIL = "operador@exemplo.test";
    private static final String IP = "203.0.113.10";

    /** Relogio controlavel: testar janela de tempo com sleep seria lento e instavel. */
    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-01-15T12:00:00Z");

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
    @DisplayName("permite exatamente MAX_FAILURES tentativas na janela")
    void allowsExactlyMaxAttempts() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            assertThat(limiter.tryAcquire(EMAIL, IP))
                    .describedAs("tentativa %d", attempt + 1)
                    .isTrue();
        }

        assertThat(limiter.tryAcquire(EMAIL, IP))
                .describedAs("tentativa alem do limite")
                .isFalse();
    }

    @Test
    @DisplayName("tentativa bloqueada nao estende o bloqueio")
    void blockedAttemptDoesNotExtendWindow() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }

        // Insiste durante o bloqueio; quem insiste nao deve ficar preso para sempre.
        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire(EMAIL, IP)).isFalse();

        // A janela conta desde as tentativas originais, nao desde a ultima recusa.
        clock.advance(LoginAttemptLimiter.WINDOW.minusMinutes(9));
        assertThat(limiter.tryAcquire(EMAIL, IP)).isTrue();
    }

    @Test
    @DisplayName("libera apos a janela expirar")
    void releasesAfterWindow() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }
        assertThat(limiter.tryAcquire(EMAIL, IP)).isFalse();

        clock.advance(LoginAttemptLimiter.WINDOW.plusSeconds(1));

        assertThat(limiter.tryAcquire(EMAIL, IP)).isTrue();
    }

    @Test
    @DisplayName("login bem-sucedido devolve a cota")
    void successRefundsQuota() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES - 1; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }
        limiter.recordSuccess(EMAIL, IP);

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            assertThat(limiter.tryAcquire(EMAIL, IP)).isTrue();
        }
    }

    @Test
    @DisplayName("bloqueio de um IP nao atinge outro IP do mesmo e-mail")
    void blockIsScopedByIp() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }

        // Caso o bloqueio fosse apenas por e-mail, um atacante travaria a conta
        // de um terceiro de proposito.
        assertThat(limiter.tryAcquire(EMAIL, "198.51.100.7")).isTrue();
    }

    @Test
    @DisplayName("bloqueio de um e-mail nao atinge outro e-mail do mesmo IP")
    void blockIsScopedByEmail() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }

        assertThat(limiter.tryAcquire("outro@exemplo.test", IP)).isTrue();
    }

    /**
     * Requisicoes concorrentes nao devem furar o limite.
     *
     * <p>Com um par {@code isBlocked()}/{@code recordFailure()}, varias threads
     * passariam pela verificacao antes de qualquer uma contabilizar, e o numero
     * de tentativas permitidas seria muito maior que o limite.
     */
    @Test
    @DisplayName("concorrencia nao permite mais tentativas que o limite")
    void concurrentAttemptsRespectLimit() throws InterruptedException {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        int threads = 64;
        var allowed = new AtomicInteger();
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        if (limiter.tryAcquire(EMAIL, IP)) {
                            allowed.incrementAndGet();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).describedAs("todas as threads terminaram").isTrue();
        }

        assertThat(allowed.get())
                .describedAs("tentativas permitidas sob concorrencia")
                .isEqualTo(LoginAttemptLimiter.MAX_FAILURES);
    }

    /**
     * O e-mail compoe a chave e e escolhido por quem chama, a partir de uma
     * rota publica. Sem teto, e-mails distintos fariam o mapa crescer
     * indefinidamente ate exaurir a memoria.
     */
    @Test
    @DisplayName("mapa nao cresce indefinidamente com chaves distintas")
    void trackedKeysStayBounded() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        int attempts = LoginAttemptLimiter.MAX_TRACKED_KEYS + 5_000;
        for (int i = 0; i < attempts; i++) {
            limiter.tryAcquire("atacante-" + i + "@exemplo.test", IP);
        }

        assertThat(limiter.trackedKeys())
                .describedAs("chaves rastreadas depois de %d e-mails distintos", attempts)
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_KEYS);
    }

    @Test
    @DisplayName("entradas expiradas sao removidas do mapa")
    void expiredEntriesAreRemoved() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        // Enche acima do limiar que dispara a limpeza.
        for (int i = 0; i < LoginAttemptLimiter.SWEEP_THRESHOLD + 100; i++) {
            limiter.tryAcquire("antigo-" + i + "@exemplo.test", IP);
        }
        int beforeExpiry = limiter.trackedKeys();

        clock.advance(LoginAttemptLimiter.WINDOW.plusMinutes(1));

        // Uma chamada apos a expiracao dispara a limpeza das entradas antigas.
        limiter.tryAcquire("novo@exemplo.test", IP);

        assertThat(limiter.trackedKeys())
                .describedAs("entradas restantes apos a janela expirar (antes: %d)", beforeExpiry)
                .isLessThan(beforeExpiry);
    }

    @Test
    @DisplayName("o bloqueio continua valendo apos uma limpeza")
    void cleanupDoesNotClearActiveBlocks() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }
        assertThat(limiter.tryAcquire(EMAIL, IP)).isFalse();

        // Pressiona o mapa para disparar a limpeza; um atacante nao deve
        // conseguir apagar o proprio bloqueio inundando o limitador.
        for (int i = 0; i < LoginAttemptLimiter.SWEEP_THRESHOLD + 100; i++) {
            limiter.tryAcquire("ruido-" + i + "@exemplo.test", IP);
        }

        assertThat(limiter.tryAcquire(EMAIL, IP))
                .describedAs("bloqueio preservado apos limpeza")
                .isFalse();
    }
}
