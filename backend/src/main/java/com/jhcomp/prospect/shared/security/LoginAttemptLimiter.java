package com.jhcomp.prospect.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Limita tentativas de login (doc 07 §4, doc 05 §12).
 *
 * <p>Implementacao em memoria, suficiente para uma instancia unica, que e o
 * cenario do MVP. Se o backend passar a rodar replicado, este contador precisa
 * migrar para armazenamento compartilhado, porque cada instancia contaria
 * separadamente.
 *
 * <p>A chave combina e-mail normalizado e IP de origem: bloquear so por e-mail
 * permitiria a um atacante travar a conta de um terceiro, e bloquear so por IP
 * atingiria usuarios legitimos atras do mesmo NAT.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failuresByKey = new ConcurrentHashMap<>();
    private final java.time.Clock clock;

    public LoginAttemptLimiter() {
        this(java.time.Clock.systemUTC());
    }

    LoginAttemptLimiter(java.time.Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String email, String clientIp) {
        Deque<Instant> failures = failuresByKey.get(key(email, clientIp));

        if (failures == null) {
            return false;
        }

        synchronized (failures) {
            pruneExpired(failures);
            return failures.size() >= MAX_FAILURES;
        }
    }

    public void recordFailure(String email, String clientIp) {
        Deque<Instant> failures =
                failuresByKey.computeIfAbsent(key(email, clientIp), unused -> new ArrayDeque<>());

        synchronized (failures) {
            pruneExpired(failures);
            failures.addLast(clock.instant());
        }
    }

    /** Login bem-sucedido zera o contador daquela origem. */
    public void recordSuccess(String email, String clientIp) {
        failuresByKey.remove(key(email, clientIp));
    }

    private void pruneExpired(Deque<Instant> failures) {
        Instant cutoff = clock.instant().minus(WINDOW);

        while (!failures.isEmpty() && failures.peekFirst().isBefore(cutoff)) {
            failures.removeFirst();
        }
    }

    private String key(String email, String clientIp) {
        return email + '|' + clientIp;
    }
}
