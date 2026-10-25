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

import org.springframework.stereotype.Component;

/**
 * Limita tentativas de login (doc 07 §4, doc 05 §12).
 *
 * <p>A chave combina e-mail normalizado e IP de origem: bloquear so por e-mail
 * permitiria a um atacante travar a conta de um terceiro, e bloquear so por IP
 * atingiria usuarios legitimos atras do mesmo NAT.
 *
 * <p><b>Reserva atomica.</b> A unica operacao de entrada e
 * {@link #tryAcquire(String, String)}, que verifica o limite e contabiliza a
 * tentativa no mesmo passo atomico. Um par
 * {@code isBlocked()}/{@code recordFailure()} seria vulneravel: entre a
 * verificacao e a contabilizacao, varias requisicoes concorrentes passariam
 * pelo teste antes de qualquer uma registrar falha, e um atacante com
 * paralelismo conseguiria muito mais que {@link #MAX_FAILURES} tentativas.
 *
 * <p><b>Memoria limitada.</b> O e-mail faz parte da chave e e escolhido por
 * quem chama, de modo que e-mails distintos fariam o mapa crescer
 * indefinidamente — exaustao de memoria a partir de uma rota publica. O
 * tamanho e contido por remocao das entradas expiradas e por um teto rigido,
 * com descarte das entradas mais antigas.
 *
 * <p><b>Escopo.</b> Implementacao em memoria, suficiente para uma instancia
 * unica, que e o cenario do MVP. Se o backend passar a rodar replicado, este
 * contador precisa migrar para armazenamento compartilhado, porque cada
 * instancia contaria separadamente.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    /**
     * Teto de chaves rastreadas. Cada entrada guarda no maximo
     * {@link #MAX_FAILURES} instantes, portanto o custo e de poucos megabytes
     * no limite.
     */
    static final int MAX_TRACKED_KEYS = 20_000;

    /** A partir deste tamanho, cada chamada tenta remover o que ja expirou. */
    static final int SWEEP_THRESHOLD = 10_000;

    /**
     * Tentativas de uma chave.
     *
     * <p>Imutavel de proposito: o valor e sempre substituido por inteiro dentro
     * de {@code compute}, nunca mutado. Isso dispensa trava interna e permite
     * que a limpeza leia os valores em paralelo com seguranca.
     */
    private record Attempts(List<Instant> timestamps) {

        Instant lastAttempt() {
            return timestamps.getLast();
        }
    }

    private final Map<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();

    /** Garante que apenas uma thread execute a limpeza por vez. */
    private final AtomicBoolean cleaning = new AtomicBoolean(false);

    private final Clock clock;

    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Reserva uma tentativa de login.
     *
     * <p>A tentativa e contabilizada no momento da reserva, e nao apenas quando
     * falha. Cobrar adiantado e o que torna a operacao atomica; um login bem
     * sucedido devolve a cota por meio de {@link #recordSuccess}.
     *
     * @return {@code true} se a tentativa pode prosseguir; {@code false} se o
     *     limite da janela foi atingido
     */
    public boolean tryAcquire(String email, String clientIp) {
        containSize();

        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        boolean[] acquired = {false};

        // compute executa de forma atomica para a chave: a verificacao do
        // limite e a contabilizacao acontecem sob a mesma trava.
        attemptsByKey.compute(key(email, clientIp), (unusedKey, current) -> {
            List<Instant> within = current == null
                    ? new ArrayList<>(MAX_FAILURES)
                    : new ArrayList<>(current.timestamps().stream().filter(cutoff::isBefore).toList());

            if (within.size() >= MAX_FAILURES) {
                acquired[0] = false;
                // Mantem a janela como esta: tentativa bloqueada nao estende o
                // bloqueio, senao quem insiste nunca sairia da punicao.
                return new Attempts(List.copyOf(within));
            }

            within.add(now);
            acquired[0] = true;
            return new Attempts(List.copyOf(within));
        });

        return acquired[0];
    }

    /** Login bem-sucedido devolve a cota daquela origem. */
    public void recordSuccess(String email, String clientIp) {
        attemptsByKey.remove(key(email, clientIp));
    }

    /** Somente para teste: numero de chaves rastreadas. */
    int trackedKeys() {
        return attemptsByKey.size();
    }

    /**
     * Mantem o mapa dentro do teto.
     *
     * <p>Primeiro remove o que expirou, que e o caso comum. Se ainda assim
     * estiver no teto, descarta as entradas mais antigas: sob ataque, perder o
     * registro de tentativas antigas e preferivel a consumir memoria sem
     * limite, e e melhor que recusar logins legitimos.
     */
    private void containSize() {
        if (attemptsByKey.size() < SWEEP_THRESHOLD) {
            return;
        }

        // Uma limpeza por vez: varias threads varrendo o mapa ao mesmo tempo
        // apenas desperdiçariam CPU no pior momento possivel.
        if (!cleaning.compareAndSet(false, true)) {
            return;
        }

        try {
            Instant cutoff = clock.instant().minus(WINDOW);
            attemptsByKey.entrySet().removeIf(entry -> entry.getValue().lastAttempt().isBefore(cutoff));

            if (attemptsByKey.size() < MAX_TRACKED_KEYS) {
                return;
            }

            // Reduz a 75% do teto para nao repetir o descarte a cada chamada.
            int target = MAX_TRACKED_KEYS * 3 / 4;
            int excess = attemptsByKey.size() - target;

            attemptsByKey.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getValue().lastAttempt()))
                    .limit(excess)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(attemptsByKey::remove);
        } finally {
            cleaning.set(false);
        }
    }

    private String key(String email, String clientIp) {
        return email + '|' + clientIp;
    }
}
