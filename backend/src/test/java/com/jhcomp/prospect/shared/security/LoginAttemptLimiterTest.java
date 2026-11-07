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

/** The login attempt limit (doc 07 §4). A unit test, without Spring. */
@DisplayName("LoginAttemptLimiter")
class LoginAttemptLimiterTest {

    private static final String EMAIL = "operador@exemplo.test";
    private static final String IP = "203.0.113.10";

    /** A controllable clock: testing a time window with sleep would be slow and flaky. */
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
    @DisplayName("allows exactly MAX_FAILURES attempts within the window")
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
    @DisplayName("a blocked attempt does not extend the block")
    void blockedAttemptDoesNotExtendWindow() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }

        // Hammering during the block; whoever hammers must not be stuck forever.
        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire(EMAIL, IP)).isFalse();

        // The window counts from the original attempts, not from the last refusal.
        clock.advance(LoginAttemptLimiter.WINDOW.minusMinutes(9));
        assertThat(limiter.tryAcquire(EMAIL, IP)).isTrue();
    }

    @Test
    @DisplayName("releases once the window expires")
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
    @DisplayName("a successful login returns the quota")
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
    @DisplayName("blocking one IP does not affect another IP for the same e-mail")
    void blockIsScopedByIp() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }

        // Were the block by e-mail alone, an attacker could deliberately lock
        // somebody else's account.
        assertThat(limiter.tryAcquire(EMAIL, "198.51.100.7")).isTrue();
    }

    @Test
    @DisplayName("blocking one e-mail does not affect another e-mail from the same IP")
    void blockIsScopedByEmail() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, IP);
        }

        assertThat(limiter.tryAcquire("outro@exemplo.test", IP)).isTrue();
    }

    /**
     * Concurrent requests must not slip past the limit.
     *
     * <p>With an {@code isBlocked()}/{@code recordFailure()} pair, several
     * threads would pass the check before any of them recorded anything, and
     * the number of attempts allowed would be far higher than the limit.
     */
    @Test
    @DisplayName("concurrency does not allow more attempts than the limit")
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

    /** The address of subscriber {@code subscriber} inside site {@code site}. */
    private static String ipv6(int site, int subscriber) {
        return "2001:db8:" + Integer.toHexString(site) + ":" + Integer.toHexString(subscriber) + "::1";
    }

    /**
     * Floods the limiter with credential keys, using each site's entire quota:
     * two subscribers per {@code /48}, each with its own quota.
     *
     * <p>It uses few networks with many e-mails each, rather than one network
     * per key: with one network per key, the site cap would stop the flood far
     * earlier.
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
    @DisplayName("the per-IP limit prevents sweeping e-mails from one origin")
    void perIpLimitStopsEmailSweep() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        int allowed = 0;
        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK * 4; i++) {
            if (limiter.tryAcquire("varredura-" + i + "@exemplo.test", IP)) {
                allowed++;
            }
        }

        // Without a network limit, every new e-mail would be a new key and the
        // origin could try indefinitely, because the credential limit would
        // never be reached.
        assertThat(allowed)
                .describedAs("tentativas permitidas de um unico IP")
                .isEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK);
        assertThat(limiter.trackedKeys())
                .describedAs("chaves criadas por um unico IP")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_ATTEMPTS_PER_NETWORK);
    }

    /**
     * Without a cap, distinct keys would make the maps grow indefinitely until
     * memory ran out, from a public route.
     */
    @Test
    @DisplayName("the maps do not grow indefinitely, however many origins there are")
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
    @DisplayName("expired entries are removed from the map")
    void expiredEntriesAreRemoved() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        floodKeys(limiter, LoginAttemptLimiter.MAX_TRACKED_KEYS / 2 + 100);
        int beforeExpiry = limiter.trackedKeys();

        clock.advance(LoginAttemptLimiter.WINDOW.plusMinutes(1));

        // One call after expiry triggers the cleanup of the old entries.
        limiter.tryAcquire("novo@exemplo.test", "192.0.2.1");

        assertThat(limiter.trackedKeys())
                .describedAs("entradas restantes apos a janela expirar (antes: %d)", beforeExpiry)
                .isLessThan(beforeExpiry);
    }

    /**
     * Eviction by age must never remove an active block.
     *
     * <p>If it did, the bypass would be direct: the attacker exhausts the
     * quota, floods the limiter to the cap, their own block leaves as the
     * oldest, and they start again. Repeat, and the attempts are unlimited.
     *
     * <p>The test blocks a site rather than a credential, because the site map
     * is the only one a flood can drive to its cap: the others are sized never
     * to saturate before it. The eviction is the same code in all three maps.
     *
     * <p>The flood has to actually reach the cap, or eviction never runs and
     * the test proves nothing; and the clock has to advance, or every entry
     * would share one instant and "oldest" would mean nothing. Those were the
     * failures of earlier versions of this test. Validated by removing the
     * blocked-entry filter from the eviction and watching the test fail.
     */
    @Test
    @DisplayName("eviction by age does not remove an active block")
    void evictionNeverRemovesActiveBlock() {
        var clock = new MutableClock();
        var limiter = new LoginAttemptLimiter(clock);

        // Exhausts the victim site's quota, varying the subscriber.
        for (int i = 0; i < LoginAttemptLimiter.MAX_ATTEMPTS_PER_SITE; i++) {
            limiter.tryAcquire("alvo-" + i + "@exemplo.test", ipv6(0xbad, i + 1));
        }
        assertThat(limiter.tryAcquire(EMAIL, ipv6(0xbad, 0xffff)))
                .describedAs("bloqueio do site estabelecido")
                .isFalse();

        // The blocked entry becomes strictly the oldest, without expiring.
        clock.advance(Duration.ofMinutes(1));

        // More sites than the cap, one attempt each: none of them is blocked,
        // and all are eviction candidates.
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
    @DisplayName("changing address inside the same /64 does not renew the quota")
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
    @DisplayName("changing /64 inside the same /48 runs into the site cap")
    void rotationWithinSiteHitsSiteCap() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        int allowed = 0;
        // Whoever holds a /48 has 65,536 /64 blocks. Without the site cap,
        // varying the fourth group would renew the quota on every attempt.
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
     * An abusive subscriber must not lock out a neighbour in the same
     * {@code /48}.
     *
     * <p>That was the side effect of grouping by {@code /48} alone: on a
     * provider handing out {@code /64}s from one {@code /48}, a single
     * customer exhausted everyone else's quota.
     */
    @Test
    @DisplayName("an abusive subscriber does not lock out a neighbour in the same /48")
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
    @DisplayName("different /48s are counted separately")
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
     * Filling the tracking maps has to be expensive.
     *
     * <p>This limiter refuses attempts from keys not yet seen once a map
     * saturates, because allowing them would be a bypass. Refusing costs
     * availability, and what makes that cost acceptable is that saturation is
     * unreachable for a single origin.
     */
    @Test
    @DisplayName("a single origin cannot saturate the tracking, however hard it tries")
    void singleOriginCannotSaturateTracking() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        // A whole /48 at its disposal, varying subscriber and address.
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

        // And the legitimate operator, on another network, still gets in.
        assertThat(limiter.tryAcquire("operador@jhcomp.local", "192.0.2.77"))
                .describedAs("login legitimo de outra rede")
                .isTrue();
    }

    @Test
    @DisplayName("saturating the tracking requires the entire site cap")
    void saturationRequiresManyDistinctSites() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        floodKeys(limiter, LoginAttemptLimiter.MAX_TRACKED_KEYS + 2_000);

        // The key map only reaches its cap once the site map already has.
        assertThat(limiter.trackedSites())
                .describedAs("sites necessarios para pressionar o teto de chaves")
                .isEqualTo(LoginAttemptLimiter.MAX_TRACKED_SITES);
        assertThat(limiter.trackedKeys())
                .describedAs("teto de chaves respeitado")
                .isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_KEYS);
    }

    @Test
    @DisplayName("an IPv4-mapped IPv6 address counts as the same IPv4")
    void mappedIpv4CountsAsIpv4() {
        var limiter = new LoginAttemptLimiter(new MutableClock());

        for (int attempt = 0; attempt < LoginAttemptLimiter.MAX_FAILURES; attempt++) {
            limiter.tryAcquire(EMAIL, "203.0.113.55");
        }

        // ::ffff:203.0.113.55 is the same host; it must not renew the quota.
        assertThat(limiter.tryAcquire(EMAIL, "::ffff:203.0.113.55"))
                .describedAs("forma mapeada do mesmo endereco")
                .isFalse();
    }
}
