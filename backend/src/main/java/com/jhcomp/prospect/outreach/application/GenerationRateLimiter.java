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
 * Limite de geracoes por usuario (doc 05 §12), em janela deslizante.
 *
 * <p>Reserva atomica, como o limitador de login (D20): conferir e contar
 * acontecem no mesmo {@code compute}, entao requisicoes concorrentes nao
 * furam o limite. O mapa cresce no maximo um item por membro que gerou
 * mensagem, por isso nao precisa de teto.
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

    /** Reserva uma geracao; falso quando o usuario esgotou a janela. */
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
