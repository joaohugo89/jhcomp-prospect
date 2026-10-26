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

    /** Endereco do assinante {@code subscriber} dentro do site {@code site}. */
    private static String ipv6(int site, int subscriber) {
        return "2001:db8:" + Integer.toHexString(site) + ":" + Integer.toHexString(subscriber) + "::1";
    }

    /**
     * Inunda o limitador com chaves de credencial, usando a cota inteira de
     * cada site: dois assinantes por {@code /48}, cada um com a sua cota.
     *
     * <p>Usa poucas redes com muitos e-mails cada, e nao uma rede por chave:
     * com uma rede por chave, o teto de sites barraria a inundacao muito antes.
     */
    private static void floodKeys(LoginAttemptLimiter limiter, int keys) {
        int perSubscriber = LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK;
        int subscribersPerSite = LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE / perSubscriber;
        int sites = keys / LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE + 1;

        for (int site = 0; site < sites; site++) {
            for (int subscriber = 0; subscriber < subscribersPerSite; subscriber++) {
                String address = ipv6(site + 1, subscriber + 1);
                for (int email = 0; email < perSubscriber; email++) {
                    limiter.tryAcquire("ruido-" + site + "-" + subscriber + "-" + email + "@exemplo.test", address);
                }
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

        // Sem limite por rede, cada e-mail novo seria uma chave nova e a origem
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
     * Sem teto, chaves distintas fariam os mapas crescerem indefinidamente ate
     * exaurir a memoria, a partir de uma rota publica.
     */
    @Test
    @DisplayName("mapas nao crescem indefinidamente, mesmo com muitas origens")
    void trackedEntriesStayBounded() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        floodKeys(limiter, LoginAttemptLimiter.MAX_TRACKED_KEYS + 5_000);

        assertThat(limiter.trackedKeys())
                .describedAs("chaves rastreadas")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_KEYS);
        assertThat(limiter.trackedSubscribers())
                .describedAs("assinantes rastreados")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_KEYS);
        assertThat(limiter.trackedSites())
                .describedAs("sites rastreados")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_SITES);
    }

    @Test
    @DisplayName("entradas expiradas sao removidas do mapa")
    void expiredEntriesAreRemoved() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        floodKeys(limiter, LoginAttemptLimiter.MAX_TRACKED_KEYS / 2 + 100);
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
     * <p>Se removesse, o bypass seria direto: o atacante esgota a cota, inunda
     * o limitador ate o teto, o seu proprio bloqueio sai por ser o mais antigo
     * e ele recomeca. Repetindo, obtem tentativas ilimitadas.
     *
     * <p>O teste bloqueia um site, e nao uma credencial, porque o mapa de sites
     * e o unico que uma inundacao consegue levar ao teto: os demais sao
     * dimensionados para nunca saturar antes dele. O descarte e o mesmo
     * codigo nos tres mapas.
     *
     * <p>A inundacao precisa de fato alcancar o teto, senao o descarte nunca
     * roda e o teste nao prova nada; e o relogio precisa avancar, senao todas
     * as entradas teriam o mesmo instante e "mais antiga" nao significaria
     * nada. Foram essas as falhas de versoes anteriores deste teste. Validado
     * removendo o filtro de bloqueados no descarte e vendo o teste falhar.
     */
    @Test
    @DisplayName("descarte por antiguidade nao remove bloqueio ativo")
    void evictionNeverRemovesActiveBlock() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        // Esgota a cota do site da vitima, variando o assinante.
        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE; i++) {
            limiter.tryAcquire("alvo-" + i + "@exemplo.test", ipv6(0xbad, i + 1));
        }
        assertThat(limiter.tryAcquire(EMAIL, ipv6(0xbad, 0xffff)))
                .describedAs("bloqueio do site estabelecido")
                .isFalse();

        // A entrada bloqueada passa a ser estritamente a mais antiga, sem
        // expirar.
        clock.advance(Duration.ofMinutes(1));

        // Mais sites que o teto, uma tentativa cada: nenhum fica bloqueado, e
        // todos sao candidatos ao descarte.
        for (int i = 0; i < LoginAttemptLimiter.MAX_TRACKED_SITES + 200; i++) {
            limiter.tryAcquire("ruido-" + i + "@exemplo.test", "198.51." + (i / 250) + "." + (i % 250));
        }

        assertThat(limiter.trackedSites())
                .describedAs("teto de sites respeitado durante a inundacao")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_SITES);
        assertThat(limiter.tryAcquire(EMAIL, ipv6(0xbad, 0xfffe)))
                .describedAs("bloqueio do site preservado apos o descarte")
                .isFalse();
    }

    @Test
    @DisplayName("trocar de endereco dentro do mesmo /64 nao renova a cota")
    void rotationWithinSubscriberDoesNotBypass() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        int allowed = 0;
        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK * 3; i++) {
            String address = "2001:db8:abcd:1:" + Integer.toHexString(i + 1) + "::1";
            if (limiter.tryAcquire("alvo-" + i + "@exemplo.test", address)) {
                allowed++;
            }
        }

        assertThat(allowed)
                .describedAs("tentativas permitidas de um unico /64")
                .isEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK);
        assertThat(limiter.trackedSubscribers()).isEqualTo(1);
    }

    @Test
    @DisplayName("trocar de /64 dentro do mesmo /48 esbarra no teto do site")
    void rotationWithinSiteHitsSiteCap() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        int allowed = 0;
        // Quem tem um /48 dispoe de 65.536 blocos /64. Sem o teto do site,
        // bastaria variar o quarto grupo para renovar a cota a cada tentativa.
        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE * 3; i++) {
            if (limiter.tryAcquire("alvo-" + i + "@exemplo.test", ipv6(0xabcd, i + 1))) {
                allowed++;
            }
        }

        assertThat(allowed)
                .describedAs("tentativas permitidas de um unico /48")
                .isEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE);
        assertThat(limiter.trackedSites()).isEqualTo(1);
        assertThat(limiter.trackedSubscribers())
                .describedAs("assinantes criados por um unico /48")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE);
    }

    /**
     * Um assinante abusivo nao pode travar o vizinho do mesmo {@code /48}.
     *
     * <p>Era o efeito colateral do agrupamento apenas por {@code /48}: num
     * provedor que distribui {@code /64} a partir de um mesmo {@code /48},
     * um cliente esgotava a cota de todos os outros.
     */
    @Test
    @DisplayName("assinante abusivo nao trava vizinho do mesmo /48")
    void abusiveSubscriberDoesNotBlockNeighbor() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK * 2; i++) {
            limiter.tryAcquire("varredura-" + i + "@exemplo.test", ipv6(0x1111, 1));
        }

        assertThat(limiter.tryAcquire("x@exemplo.test", ipv6(0x1111, 1)))
                .describedAs("assinante abusivo bloqueado")
                .isFalse();
        assertThat(limiter.tryAcquire(EMAIL, ipv6(0x1111, 2)))
                .describedAs("vizinho no mesmo /48")
                .isTrue();
    }

    @Test
    @DisplayName("/48 diferentes sao contados separadamente")
    void differentSitesAreIndependent() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE; i++) {
            limiter.tryAcquire("a-" + i + "@exemplo.test", ipv6(0x1111, i + 1));
        }

        assertThat(limiter.tryAcquire("b@exemplo.test", ipv6(0x1111, 0xfff0)))
                .describedAs("mesmo /48, teto esgotado")
                .isFalse();
        assertThat(limiter.tryAcquire("b@exemplo.test", ipv6(0x2222, 1)))
                .describedAs("/48 distinto")
                .isTrue();
    }

    /**
     * Encher o rastreamento tem de ser caro.
     *
     * <p>Este limitador recusa tentativas de chaves ainda nao vistas quando um
     * mapa satura, porque permiti-las seria bypass. Recusar tem custo de
     * disponibilidade, e o que torna esse custo aceitavel e a saturacao ser
     * inalcancavel para uma origem unica.
     */
    @Test
    @DisplayName("uma origem unica nao satura o rastreamento, por mais que tente")
    void singleOriginCannotSaturateTracking() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        // Um /48 inteiro a disposicao, variando assinante e endereco.
        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE * 100; i++) {
            limiter.tryAcquire("alvo-" + i + "@exemplo.test", ipv6(0x9999, (i % 65_535) + 1));
        }

        assertThat(limiter.trackedSites()).isEqualTo(1);
        assertThat(limiter.trackedSubscribers())
                .describedAs("assinantes criados por uma origem unica")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE);
        assertThat(limiter.trackedKeys())
                .describedAs("chaves criadas por uma origem unica")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE);

        // E o operador legitimo, em outra rede, continua entrando.
        assertThat(limiter.tryAcquire("operador@jhcomp.local", "192.0.2.77"))
                .describedAs("login legitimo de outra rede")
                .isTrue();
    }

    @Test
    @DisplayName("saturar o rastreamento exige o teto inteiro de sites")
    void saturationRequiresManyDistinctSites() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        floodKeys(limiter, LoginAttemptLimiter.MAX_TRACKED_KEYS + 2_000);

        // O mapa de chaves so chega ao teto quando o de sites ja chegou.
        assertThat(limiter.trackedSites())
                .describedAs("sites necessarios para pressionar o teto de chaves")
                .isEqualTo(LoginAttemptLimiter.MAX_TRACKED_SITES);
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
