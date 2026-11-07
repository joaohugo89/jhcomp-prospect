package com.jhcomp.prospect.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Rate limits login attempts (doc 07 §4, doc 05 §12, D20).
 *
 * <p>Three independent limits apply, in this order:
 *
 * <ol>
 *   <li><b>Per site</b> ({@link #MAX_ATTEMPTS_PER_SITE} per window): the
 *       aggregate ceiling of an IPv6 {@code /48}. It stops whoever holds a
 *       whole block from multiplying quotas by rotating {@code /64}s.
 *   <li><b>Per subscriber</b> ({@link #MAX_ATTEMPTS_PER_NETWORK} per window):
 *       an IPv6 {@code /64}, or an exact IPv4 address. It caps one origin's
 *       volume whatever the e-mail, without hitting the neighbours in the
 *       same {@code /48}.
 *   <li><b>Per e-mail and subscriber</b> ({@link #MAX_FAILURES} per window):
 *       it stops someone hammering one specific credential.
 * </ol>
 *
 * <p>The credential limit alone is not enough: the e-mail is part of the key
 * and is chosen by the caller, so a single origin could try a different e-mail
 * every time, never be stopped, and fill the tracking maps as a bonus. The
 * network limits close that way out.
 *
 * <p>The credential key combines e-mail <em>and</em> network, not e-mail
 * alone: blocking by e-mail only would let an attacker deliberately lock
 * somebody else's account.
 *
 * <p><b>Atomic reservation.</b> The only entry point is
 * {@link #tryAcquire(String, String)}, which checks the limit and records the
 * attempt in the same step. An {@code isBlocked()}/{@code recordFailure()}
 * pair would be vulnerable: between the check and the accounting, concurrent
 * requests would all pass the test before any of them recorded a failure.
 *
 * <p><b>Bounded memory that opens no bypass.</b> Every map has a cap. On
 * reaching it, expired entries go first and then the oldest ones <b>that are
 * not blocked</b>. Evicting a blocked entry would be a direct bypass: flood
 * the limiter, erase your own block, start over. With no evictable candidate,
 * new keys are refused rather than making room by dropping active blocks.
 *
 * <p><b>Scope.</b> An in-memory implementation, valid per instance, which is
 * the MVP's scenario. If the backend is ever replicated these counters have to
 * move to shared storage, because each instance would count separately.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    /** Attempts per e-mail and subscriber within the window. */
    static final int MAX_FAILURES = 5;

    /**
     * Attempts per subscriber ({@code /64} or IPv4) within the window,
     * whatever the e-mail.
     *
     * <p>Ten times the credential limit: enough slack for an operator to
     * mistype a password a few times, while still stopping one origin from
     * sweeping e-mails or filling the tracking maps.
     */
    static final int MAX_ATTEMPTS_PER_NETWORK = 50;

    /**
     * Attempts per site ({@code /48}) within the window, across all of its
     * subscribers.
     *
     * <p>Twice a subscriber's quota: an abusive neighbour in the same
     * {@code /48} does not lock out the rest, and whoever controls the whole
     * block gets only twice a normal origin's allowance, not 65,536 times it.
     * For IPv4 the site and the subscriber coincide, and the subscriber quota
     * applies.
     */
    static final int MAX_ATTEMPTS_PER_SITE = 2 * MAX_ATTEMPTS_PER_NETWORK;

    static final Duration WINDOW = Duration.ofMinutes(15);

    /**
     * Cap on tracked sites.
     *
     * <p>A thousand distinct {@code /48} blocks or IPv4 addresses cannot be
     * obtained by rotating addresses: they require networks from different
     * operators.
     */
    static final int MAX_TRACKED_SITES = 1_000;

    /**
     * Cap on tracked subscribers and credential keys.
     *
     * <p>Sized as {@link #MAX_TRACKED_SITES} × {@link #MAX_ATTEMPTS_PER_SITE}:
     * each attempt creates at most one entry per map, and each site only has
     * {@link #MAX_ATTEMPTS_PER_SITE} attempts available, so neither of these
     * maps saturates before the site map does. That sizing is what makes a
     * refusal by saturation a sign of a severe distributed attack rather than
     * something a single origin can reach.
     *
     * <p>Memory tracks the total number of attempts, not of keys: in the worst
     * case, a hundred thousand instants per map.
     */
    static final int MAX_TRACKED_KEYS = MAX_TRACKED_SITES * MAX_ATTEMPTS_PER_SITE;

    /**
     * Minimum interval between sweeps of expired entries.
     *
     * <p>Without it, above the threshold every attempt would scan the whole
     * map: under attack that costs O(n) per request, exactly when the server
     * is under most pressure. Cap-driven eviction ignores this interval,
     * because there the memory limit has to apply immediately.
     */
    static final Duration SWEEP_INTERVAL = Duration.ofSeconds(10);

    private final Bucket bySite = new Bucket("sites", MAX_ATTEMPTS_PER_SITE, MAX_TRACKED_SITES);
    private final Bucket bySubscriber = new Bucket("assinantes", MAX_ATTEMPTS_PER_NETWORK, MAX_TRACKED_KEYS);
    private final Bucket byCredential = new Bucket("credenciais", MAX_FAILURES, MAX_TRACKED_KEYS);

    private final Clock clock;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Reserves one login attempt.
     *
     * <p>The attempt is charged at reservation time, not only when it fails:
     * charging up front is what makes the operation atomic. A successful login
     * returns the credential's quota through {@link #recordSuccess}.
     *
     * <p>The limits are evaluated from widest to narrowest: it is the widest
     * one that stops an origin from creating entries in the others. A refusal
     * at one level does not refund what the earlier ones already charged, on
     * purpose: the attempt happened and counts towards the origin's volume.
     *
     * @return {@code true} when the attempt may proceed
     */
    public boolean tryAcquire(String email, String clientIp) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        ClientNetwork network = ClientNetwork.of(clientIp);

        if (!bySite.tryAcquire(network.site(), now, cutoff)) {
            return false;
        }

        if (!bySubscriber.tryAcquire(network.subscriber(), now, cutoff)) {
            // Refund the site: an already blocked subscriber that keeps
            // hammering must not drain the shared quota and lock out the
            // neighbours. Rotating /64 still costs the site, because each new
            // /64 arrives here with its quota free.
            bySite.refund(network.site(), now);
            return false;
        }

        return byCredential.tryAcquire(key(email, network), now, cutoff);
    }

    /**
     * A successful login returns that credential's quota.
     *
     * <p>The network quotas are deliberately not returned: they measure the
     * origin's attempt volume, and one success after many attempts is exactly
     * the pattern worth continuing to limit.
     */
    public void recordSuccess(String email, String clientIp) {
        byCredential.release(key(email, ClientNetwork.of(clientIp)));
    }

    /** For tests only. */
    int trackedKeys() {
        return byCredential.size();
    }

    /** For tests only. */
    int trackedSubscribers() {
        return bySubscriber.size();
    }

    /** For tests only. */
    int trackedSites() {
        return bySite.size();
    }

    private static String key(String email, ClientNetwork network) {
        return email + '|' + network.subscriber();
    }

    /**
     * The attempts recorded for one key.
     *
     * <p>Immutable on purpose: the value is always replaced wholesale inside
     * {@code compute}, never mutated. That removes the need for an internal
     * lock and lets the size containment read values concurrently and
     * safely.
     */
    private record Attempts(List<Instant> timestamps) {

        Instant lastAttempt() {
            return timestamps.getLast();
        }

        boolean isBlocked(Instant cutoff, int maxAttempts) {
            return timestamps.stream().filter(cutoff::isBefore).count() >= maxAttempts;
        }
    }

    /**
     * One independent limit: a map of keys with a cap on attempts and on
     * size.
     */
    private static final class Bucket {

        private final String name;
        private final Map<String, Attempts> entries = new ConcurrentHashMap<>();
        private final AtomicBoolean cleaning = new AtomicBoolean(false);
        private volatile Instant lastSweepAt;
        private volatile Instant lastSaturationLogAt;
        private final int maxAttempts;
        private final int maxKeys;
        private final int sweepThreshold;

        Bucket(String name, int maxAttempts, int maxKeys) {
            this.name = name;
            this.maxAttempts = maxAttempts;
            this.maxKeys = maxKeys;
            this.sweepThreshold = maxKeys / 2;
        }

        boolean tryAcquire(String key, Instant now, Instant cutoff) {
            contain(now, cutoff);

            boolean[] acquired = {false};

            // compute runs atomically for the key: the limit check and the
            // accounting happen under the same lock.
            entries.compute(key, (unusedKey, current) -> {
                if (current == null && entries.size() >= maxKeys) {
                    // At the cap with nothing evictable, the attempt is
                    // refused.
                    //
                    // Allowing an untracked attempt here would be a bypass:
                    // from saturation onwards the limit would stop applying to
                    // keys not yet seen.
                    //
                    // Refusing costs availability, which is why saturation was
                    // made expensive rather than tolerated: the caps are sized
                    // so that reaching this point requires MAX_TRACKED_SITES
                    // distinct networks from different operators. A single
                    // origin, even with a whole /48 at its disposal, is stopped
                    // long before by the site cap.
                    //
                    // In that state an in-memory limiter is no longer the right
                    // defence; edge filtering is. See D20.
                    reportSaturation(now);
                    acquired[0] = false;
                    return null;
                }

                List<Instant> within = current == null
                        ? new ArrayList<>(maxAttempts)
                        : new ArrayList<>(current.timestamps().stream().filter(cutoff::isBefore).toList());

                if (within.size() >= maxAttempts) {
                    acquired[0] = false;
                    // Keep the window as it is: a refused attempt does not
                    // extend the block, or whoever keeps hammering would never
                    // serve out the penalty.
                    return new Attempts(List.copyOf(within));
                }

                within.add(now);
                acquired[0] = true;
                return new Attempts(List.copyOf(within));
            });

            return acquired[0];
        }

        void release(String key) {
            entries.remove(key);
        }

        /** Undoes an attempt charged at {@code at}, if it is still there. */
        void refund(String key, Instant at) {
            entries.computeIfPresent(key, (unusedKey, current) -> {
                List<Instant> remaining = new ArrayList<>(current.timestamps());
                remaining.remove(at);
                return remaining.isEmpty() ? null : new Attempts(List.copyOf(remaining));
            });
        }

        /**
         * Logs saturation, at most once per window.
         *
         * <p>Saturation means a distributed attack is under way and has to be
         * visible to whoever operates the system: without the log, the observed
         * symptom would simply be "nobody can log in", with no apparent
         * cause.
         */
        private void reportSaturation(Instant now) {
            Instant last = lastSaturationLogAt;
            if (last != null && last.isAfter(now.minus(WINDOW))) {
                return;
            }
            lastSaturationLogAt = now;
            log.error(
                    "Limitador de login saturado ({}): {} entradas rastreadas, teto {}. "
                            + "Tentativas de chaves ainda nao vistas serao recusadas. "
                            + "Indica ataque distribuido; avaliar filtragem na borda.",
                    name,
                    entries.size(),
                    maxKeys);
        }

        int size() {
            return entries.size();
        }

        /**
         * Keeps the map within its cap.
         *
         * <p>Expired entries go first, which is the common case. If it is still
         * at the cap, the oldest entries <b>among the unblocked ones</b> are
         * evicted. A blocked entry is never evicted: without that restriction,
         * flooding the limiter would erase the attacker's own block and hand
         * their attempts back.
         */
        private void contain(Instant now, Instant cutoff) {
            int currentSize = entries.size();

            if (currentSize < sweepThreshold) {
                return;
            }

            boolean overCap = currentSize >= maxKeys;

            // Below the cap the sweep is spaced out over time. At the cap it
            // runs immediately: the memory limit cannot wait.
            if (!overCap && lastSweepAt != null && lastSweepAt.isAfter(now.minus(SWEEP_INTERVAL))) {
                return;
            }

            // One sweep at a time: several threads scanning the map at once
            // would only waste CPU at the worst possible moment.
            if (!cleaning.compareAndSet(false, true)) {
                return;
            }

            try {
                lastSweepAt = now;
                entries.entrySet().removeIf(entry -> entry.getValue().lastAttempt().isBefore(cutoff));

                if (entries.size() < maxKeys) {
                    return;
                }

                // Drop to 75% of the cap so eviction does not repeat on every call.
                int excess = entries.size() - (maxKeys * 3 / 4);

                entries.entrySet().stream()
                        .filter(entry -> !entry.getValue().isBlocked(cutoff, maxAttempts))
                        .sorted(Comparator.comparing(entry -> entry.getValue().lastAttempt()))
                        .limit(excess)
                        .map(Map.Entry::getKey)
                        .toList()
                        .forEach(entries::remove);
            } finally {
                cleaning.set(false);
            }
        }
    }
}
