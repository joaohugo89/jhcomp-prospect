package com.jhcomp.prospect.outreach.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * A per-user generation limit (doc 05 §12), over a sliding window.
 *
 * <p>Atomic reservation, like the login limiter (D20): checking and counting
 * happen in the same {@code compute}, so concurrent requests cannot slip
 * through. The map grows by at most one entry per member who has generated a
 * message, which is why it needs no cap.
 */
@Component
class GenerationRateLimiter {

    static final int MAX_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<UUID, Deque<Instant>> recent = new ConcurrentHashMap<>();
    private final Clock clock;

    GenerationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Reserves one generation; false when the user has exhausted the window. */
    boolean tryAcquire(UUID userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);
        boolean[] acquired = {false};
        recent.compute(userId, (key, times) -> {
            Deque<Instant> window = times == null ? new ArrayDeque<>() : times;
            while (!window.isEmpty() && !window.peekFirst().isAfter(windowStart)) {
                window.pollFirst();
            }
            if (window.size() < MAX_PER_WINDOW) {
                window.addLast(now);
                acquired[0] = true;
            }
            return window;
        });
        return acquired[0];
    }
}
