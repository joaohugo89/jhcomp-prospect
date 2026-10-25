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
     * Inunda o mapa de chaves ate passar do teto.
     *
     * <p>Usa poucos IPs com muitos e-mails cada, e nao um IP por chave: com um
     * IP por chave, o teto de IPs barraria a inundacao antes de o mapa de
     * chaves alcancar o seu proprio teto, e o descarte nunca seria exercitado.
     * Cada IP respeita {@code MAX_ATTEMPTS_PER_NETWORK}.
     */
    private static void flood(LoginAttemptLimiter limiter, int keys) {
        int perIp = LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK;
        int ips = keys / perIp + 1;

        for (int ipIndex = 0; ipIndex < ips; ipIndex++) {
            String ip = "198.51." + (ipIndex / 250) + "." + (ipIndex % 250);
            for (int emailIndex = 0; emailIndex < perIp; emailIndex++) {
                limiter.tryAcquire("ruido-" + ipIndex + "-" + emailIndex + "@exemplo.test", ip);
            }
        }
    }

    @Test
    @DisplayName("limite por IP impede varrer e-mails de uma mesma origem")
    void perIpLimitStopsEmailSweep() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        int allowed = 0;
        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK * 4; i++) {
            if (limiter.tryAcquire("varredura-" + i + "@exemplo.test", IP)) {
                allowed++;
            }
        }

        // Sem limite por IP, cada e-mail novo seria uma chave nova e a origem
        // tentaria indefinidamente, pois o limite por credencial nunca seria
        // atingido.
        assertThat(allowed)
                .describedAs("tentativas permitidas de um unico IP")
                .isEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK);
        assertThat(limiter.trackedKeys())
                .describedAs("chaves criadas por um unico IP")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK);
    }

    /**
     * Sem teto, chaves distintas fariam o mapa crescer indefinidamente ate
     * exaurir a memoria, a partir de uma rota publica.
     */
    @Test
    @DisplayName("mapa nao cresce indefinidamente, mesmo com muitas origens")
    void trackedKeysStayBounded() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        flood(limiter, LoginAttemptLimiter.MAX_TRACKED_KEYS + 5_000);

        assertThat(limiter.trackedKeys())
                .describedAs("chaves rastreadas")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_KEYS);
        assertThat(limiter.trackedNetworks())
                .describedAs("IPs rastreados")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_NETWORKS);
    }

    @Test
    @DisplayName("entradas expiradas sao removidas do mapa")
    void expiredEntriesAreRemoved() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        flood(limiter, LoginAttemptLimiter.MAX_TRACKED_KEYS / 2 + 100);
        int beforeExpiry = limiter.trackedKeys();

        clock.advance(LoginAttemptLimiter.WINDOW.plusMinutes(1));

        // Uma chamada apos a expiracao dispara a limpeza das entradas antigas.
        limiter.tryAcquire("novo@exemplo.test", "192.0.2.1");

        assertThat(limiter.trackedKeys())
                .describedAs("entradas restantes apos a janela expirar (antes: %d)", beforeExpiry)
                .isLessThan(beforeExpiry);
    }

    /**
     * O descarte por antiguidade nunca pode remover um bloqueio ativo.
     *
     * <p>Se removesse, o bypass seria direto: o atacante queima as tentativas
     * contra a vitima, inunda o limitador ate o teto, o seu proprio bloqueio
     * sai por ser o mais antigo e ele recomeca. Repetindo, obtem tentativas
     * ilimitadas.
     *
     * <p>A inundacao precisa de fato alcancar o teto de chaves, senao o
     * descarte nunca roda e o teste nao prova nada. Ver o comentario de
     * {@code flood}: foi exatamente essa a falha das duas versoes anteriores
     * deste teste.
     */
    @Test
    @DisplayName("descarte por antiguidade nao remove bloqueio ativo")
    void evictionNeverRemovesActiveBlock() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        // Bloqueia a credencial da vitima.
        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }
        assertThat(limiter.tryAcquire(EMAIL, IP))
                .describedAs("bloqueio estabelecido")
                .isFalse();

        // Avanca menos que a janela: a entrada da vitima passa a ser
        // estritamente a mais antiga, sem expirar. Sem este avanco todas as
        // entradas teriam o mesmo instante, "mais antiga" nao significaria nada
        // e a ordem do descarte seria arbitraria — o teste poderia passar por
        // sorte mesmo com a protecao removida.
        clock.advance(java.time.Duration.ofMinutes(1));

        // Inunda acima do teto para forcar o descarte por antiguidade.
        flood(limiter, LoginAttemptLimiter.MAX_TRACKED_KEYS + 2_000);

        assertThat(limiter.trackedKeys())
                .describedAs("teto respeitado durante a inundacao")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_KEYS);
        assertThat(limiter.tryAcquire(EMAIL, IP))
                .describedAs("bloqueio preservado apos o descarte")
                .isFalse();
    }

    @Test
    @DisplayName("trocar de endereco dentro do mesmo /48 nao renova a cota")
    void ipv6RotationWithinPrefixDoesNotBypass() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        int allowed = 0;
        // Quem tem um /48 dispoe de 65.536 blocos /64. Se o limite fosse por
        // endereco, ou mesmo por /64, bastaria variar o quarto grupo para
        // renovar a cota a cada tentativa.
        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK * 3; i++) {
            String address = "2001:db8:abcd:" + Integer.toHexString(i + 1) + "::1";
            if (limiter.tryAcquire("alvo-" + i + "@exemplo.test", address)) {
                allowed++;
            }
        }

        assertThat(allowed)
                .describedAs("tentativas permitidas de um unico /48")
                .isEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK);
        assertThat(limiter.trackedNetworks())
                .describedAs("redes rastreadas para um unico /48")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("/48 diferentes sao contados separadamente")
    void differentPrefixesAreIndependent() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK; i++) {
            limiter.tryAcquire("a-" + i + "@exemplo.test", "2001:db8:1111:1111::5");
        }

        assertThat(limiter.tryAcquire("b@exemplo.test", "2001:db8:1111:1111::9"))
                .describedAs("mesmo /48, cota esgotada")
                .isFalse();
        assertThat(limiter.tryAcquire("b@exemplo.test", "2001:db8:2222:2222::9"))
                .describedAs("/48 distinto")
                .isTrue();
    }

    /**
     * Encher o rastreamento tem de ser caro.
     *
     * <p>Este limitador recusa tentativas de credenciais ainda nao vistas
     * quando o mapa satura, porque permiti-las seria bypass do limite por
     * credencial. Recusar tem custo de disponibilidade, e o que torna esse
     * custo aceitavel e a saturacao ser inalcancavel para uma origem unica: os
     * tetos sao dimensionados de modo que cada rede so cria tantas chaves
     * quantas tentativas lhe sao permitidas.
     *
     * <p>Antes do agrupamento por {@code /48}, uma unica origem com um bloco
     * IPv6 saturava o mapa de graca, e a recusa virava bloqueio global de
     * login a custo baixo.
     */
    @Test
    @DisplayName("uma origem unica nao satura o rastreamento, por mais que tente")
    void singleOriginCannotSaturateTracking() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        // Um /48 inteiro a disposicao, variando endereco a cada tentativa.
        for (int i = 0; i < LoginAttemptLimiter.MAX_TRACKED_KEYS * 2; i++) {
            limiter.tryAcquire(
                    "alvo-" + i + "@exemplo.test", "2001:db8:9999:" + Integer.toHexString(i % 65_536) + "::1");
        }

        assertThat(limiter.trackedNetworks())
                .describedAs("redes rastreadas a partir de um unico /48")
                .isEqualTo(1);
        assertThat(limiter.trackedKeys())
                .describedAs("chaves criadas por uma origem unica")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK);

        // E o operador legitimo, em outra rede, continua entrando.
        assertThat(limiter.tryAcquire("operador@jhcomp.local", "192.0.2.77"))
                .describedAs("login legitimo de outra rede")
                .isTrue();
    }

    @Test
    @DisplayName("saturar o rastreamento exige muitas redes distintas")
    void saturationRequiresManyDistinctNetworks() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        flood(limiter, LoginAttemptLimiter.MAX_TRACKED_KEYS + 2_000);

        // A inundacao so chega perto do teto usando o teto de redes inteiro.
        assertThat(limiter.trackedNetworks())
                .describedAs("redes necessarias para pressionar o teto de chaves")
                .isGreaterThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_NETWORKS / 2);
        assertThat(limiter.trackedKeys())
                .describedAs("teto de chaves respeitado")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_KEYS);
    }

    @Test
    @DisplayName("IPv4 mapeado em IPv6 conta como o mesmo IPv4")
    void mappedIpv4CountsAsIpv4() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, "203.0.113.55");
        }

        // ::ffff:203.0.113.55 e o mesmo host; nao deve renovar a cota.
        assertThat(limiter.tryAcquire(EMAIL, "::ffff:203.0.113.55"))
                .describedAs("forma mapeada do mesmo endereco")
                .isFalse();
    }
}
