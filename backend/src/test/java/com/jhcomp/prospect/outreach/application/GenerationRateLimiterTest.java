package com.jhcomp.prospect.outreach.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The per-user message generation limit")
class GenerationRateLimiterTest {

    /** A clock that advances at the test's command. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-06T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final GenerationRateLimiter limiter = new GenerationRateLimiter(clock);

    @Test
    @DisplayName("allows up to the cap within the window and refuses the next one")
    void refusesAboveLimit() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < GenerationRateLimiter.MAX_PER_WINDOW; i++) {
            assertThat(limiter.tryAcquire(user)).as("geracao %d", i + 1).isTrue();
            clock.advance(Duration.ofSeconds(1));
        }

        assertThat(limiter.tryAcquire(user)).isFalse();
        assertThat(limiter.tryAcquire(UUID.randomUUID())).as("outro usuario").isTrue();
    }

    @Test
    @DisplayName("the window slides: the oldest generation expires and frees a slot")
    void windowSlides() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < GenerationRateLimiter.MAX_PER_WINDOW; i++) {
            limiter.tryAcquire(user);
            clock.advance(Duration.ofSeconds(10));
        }
        assertThat(limiter.tryAcquire(user)).isFalse();

        // The last reservation happened 10 s ago; the first, 100 s ago.
        clock.advance(GenerationRateLimiter.WINDOW.minusSeconds(100));
        assertThat(limiter.tryAcquire(user)).as("primeira expirou").isTrue();
        assertThat(limiter.tryAcquire(user)).as("so uma vaga liberada").isFalse();
    }

    @Test
    @DisplayName("concurrent requests do not slip past the cap")
    void concurrentRequestsRespectLimit() throws Exception {
        UUID user = UUID.randomUUID();
        int threads = 64;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger acquired = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                start.await();
                if (limiter.tryAcquire(user)) {
                    acquired.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(acquired).hasValue(GenerationRateLimiter.MAX_PER_WINDOW);
    }
}
