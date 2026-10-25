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
 * Limita tentativas de login (doc 07 §4, doc 05 §12).
 *
 * <p>Aplica dois limites independentes, nesta ordem:
 *
 * <ol>
 *   <li><b>Por IP</b> ({@link #MAX_ATTEMPTS_PER_IP} por janela): limita o
 *       volume total de tentativas de uma origem, qualquer que seja o e-mail.
 *   <li><b>Por e-mail e IP</b> ({@link #MAX_FAILURES} por janela): bloqueia a
 *       insistencia contra uma credencial especifica.
 * </ol>
 *
 * <p>O limite por credencial sozinho nao basta: o e-mail faz parte da chave e e
 * escolhido por quem chama, de modo que uma origem unica poderia tentar
 * e-mails sempre diferentes sem nunca ser barrada, e ainda encher o
 * rastreamento. O limite por IP fecha essa saida, porque o endereco de origem
 * nao e livremente escolhido por um atacante nao distribuido.
 *
 * <p>A chave do segundo limite combina e-mail e IP, e nao apenas e-mail:
 * bloquear so por e-mail permitiria a um atacante travar a conta de um
 * terceiro de proposito.
 *
 * <p><b>Reserva atomica.</b> A unica operacao de entrada e
 * {@link #tryAcquire(String, String)}, que verifica o limite e contabiliza a
 * tentativa no mesmo passo. Um par {@code isBlocked()}/{@code recordFailure()}
 * seria vulneravel: entre a verificacao e a contabilizacao, requisicoes
 * concorrentes passariam todas pelo teste antes de qualquer uma registrar
 * falha.
 *
 * <p><b>Memoria limitada sem abrir bypass.</b> Cada mapa tem teto. Ao atingi-lo,
 * removem-se primeiro as entradas expiradas e, depois, as mais antigas
 * <b>que nao estejam bloqueadas</b>. Descartar entrada bloqueada seria um
 * bypass direto: bastaria inundar o limitador para apagar o proprio bloqueio e
 * recomecar. Quando nao ha candidato descartavel, novas chaves sao recusadas em
 * vez de se abrir espaco removendo bloqueios ativos.
 *
 * <p><b>Escopo.</b> Implementacao em memoria, valida por instancia, que e o
 * cenario do MVP. Se o backend passar a rodar replicado, estes contadores
 * precisam migrar para armazenamento compartilhado, porque cada instancia
 * contaria separadamente.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    /** Tentativas por e-mail e rede dentro da janela. */
    static final int MAX_FAILURES = 5;

    /**
     * Tentativas por rede de origem dentro da janela, qualquer que seja o
     * e-mail.
     *
     * <p>Dez vezes o limite por credencial: sobra folga para o operador errar a
     * senha algumas vezes, e ainda assim uma origem nao consegue varrer
     * e-mails nem encher o rastreamento.
     */
    static final int MAX_ATTEMPTS_PER_NETWORK = 50;

    static final Duration WINDOW = Duration.ofMinutes(15);

    /**
     * Teto de redes rastreadas.
     *
     * <p>Mil blocos {@code /48} distintos nao sao obtidos por rotacao de
     * endereco: exigem redes de operadores diferentes.
     */
    static final int MAX_TRACKED_NETWORKS = 1_000;

    /**
     * Teto de chaves de credencial rastreadas.
     *
     * <p>Dimensionado como {@link #MAX_TRACKED_NETWORKS} x
     * {@link #MAX_ATTEMPTS_PER_NETWORK}: cada rede so consegue criar tantas
     * chaves quantas tentativas lhe sao permitidas, portanto este mapa nao
     * satura antes do mapa de redes. E esse dimensionamento que faz a recusa
     * por saturacao ser um estado de ataque severo, e nao algo alcancavel por
     * uma origem unica.
     */
    static final int MAX_TRACKED_KEYS = MAX_TRACKED_NETWORKS * MAX_ATTEMPTS_PER_NETWORK;

    /**
     * Intervalo minimo entre limpezas de entradas expiradas.
     *
     * <p>Sem ele, acima do limiar cada tentativa varreria o mapa inteiro: sob
     * ataque isso custa O(n) por requisicao, exatamente quando o servidor esta
     * mais pressionado. O descarte por teto nao respeita este intervalo, porque
     * nesse caso o limite de memoria precisa valer de imediato.
     */
    static final Duration SWEEP_INTERVAL = Duration.ofSeconds(10);

    private final Bucket byEmailAndNetwork = new Bucket(MAX_FAILURES, MAX_TRACKED_KEYS);
    private final Bucket byNetwork = new Bucket(MAX_ATTEMPTS_PER_NETWORK, MAX_TRACKED_NETWORKS);

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
     * <p>A tentativa e contabilizada na reserva, e nao apenas quando falha:
     * cobrar adiantado e o que torna a operacao atomica. Um login bem sucedido
     * devolve a cota da credencial por meio de {@link #recordSuccess}.
     *
     * @return {@code true} se a tentativa pode prosseguir
     */
    public boolean tryAcquire(String email, String clientIp) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        String network = ClientNetwork.of(clientIp);

        // O limite por rede e avaliado primeiro: e ele que impede uma origem de
        // criar chaves sem limite variando o e-mail ou o endereco.
        if (!byNetwork.tryAcquire(network, now, cutoff)) {
            return false;
        }

        return byEmailAndNetwork.tryAcquire(key(email, network), now, cutoff);
    }

    /**
     * Login bem-sucedido devolve a cota daquela credencial.
     *
     * <p>A cota do IP nao e devolvida de proposito: ela mede o volume de
     * tentativas da origem, e um acerto depois de muitas tentativas e
     * justamente o padrao que se quer continuar limitando.
     */
    public void recordSuccess(String email, String clientIp) {
        byEmailAndNetwork.release(key(email, ClientNetwork.of(clientIp)));
    }

    /** Somente para teste. */
    int trackedKeys() {
        return byEmailAndNetwork.size();
    }

    /** Somente para teste. */
    int trackedNetworks() {
        return byNetwork.size();
    }

    private String key(String email, String network) {
        return email + '|' + network;
    }

    /**
     * Tentativas de uma chave.
     *
     * <p>Imutavel de proposito: o valor e sempre substituido por inteiro dentro
     * de {@code compute}, nunca mutado. Isso dispensa trava interna e permite
     * que a contencao de tamanho leia os valores em paralelo com seguranca.
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
     * Um limite independente: mapa de chaves com teto de tentativas e de
     * tamanho.
     */
    private static final class Bucket {

        private final Map<String, Attempts> entries = new ConcurrentHashMap<>();
        private final AtomicBoolean cleaning = new AtomicBoolean(false);
        private volatile Instant lastSweepAt;
        private volatile Instant lastSaturationLogAt;
        private final int maxAttempts;
        private final int maxKeys;
        private final int sweepThreshold;

        Bucket(int maxAttempts, int maxKeys) {
            this.maxAttempts = maxAttempts;
            this.maxKeys = maxKeys;
            this.sweepThreshold = maxKeys / 2;
        }

        boolean tryAcquire(String key, Instant now, Instant cutoff) {
            contain(now, cutoff);

            boolean[] acquired = {false};

            // compute executa de forma atomica para a chave: a verificacao do
            // limite e a contabilizacao acontecem sob a mesma trava.
            entries.compute(key, (unusedKey, current) -> {
                if (current == null && entries.size() >= maxKeys) {
                    // No teto e sem espaco descartavel, a tentativa e recusada.
                    //
                    // Permitir tentativa nao rastreada aqui seria bypass: a
                    // partir da saturacao, o limite por credencial deixaria de
                    // valer para contas ainda nao vistas.
                    //
                    // Recusar tem custo de disponibilidade, e por isso a
                    // saturacao foi encarecida em vez de tolerada: os tetos sao
                    // dimensionados para que chegar aqui exija
                    // MAX_TRACKED_NETWORKS blocos /48 distintos, de operadores
                    // diferentes. Uma origem unica, mesmo com um /48 inteiro a
                    // disposicao, e barrada muito antes pelo limite por rede.
                    //
                    // Nesse estado, um limitador em memoria ja nao e a defesa
                    // adequada: o caminho e filtragem na borda. Ver D20.
                    reportSaturation(now);
                    acquired[0] = false;
                    return null;
                }

                List<Instant> within = current == null
                        ? new ArrayList<>(maxAttempts)
                        : new ArrayList<>(current.timestamps().stream().filter(cutoff::isBefore).toList());

                if (within.size() >= maxAttempts) {
                    acquired[0] = false;
                    // Mantem a janela como esta: tentativa recusada nao estende
                    // o bloqueio, senao quem insiste nunca sairia da punicao.
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

        /**
         * Registra a saturacao, no maximo uma vez por janela.
         *
         * <p>Saturacao significa ataque distribuido em curso e precisa ser
         * visivel na operacao: sem registro, o sintoma observado seria apenas
         * "ninguem consegue entrar", sem causa aparente.
         */
        private void reportSaturation(Instant now) {
            Instant last = lastSaturationLogAt;
            if (last != null && last.isAfter(now.minus(WINDOW))) {
                return;
            }
            lastSaturationLogAt = now;
            log.error(
                    "Limitador de login saturado: {} chaves rastreadas, teto {}. "
                            + "Tentativas de credenciais ainda nao vistas serao recusadas. "
                            + "Indica ataque distribuido; avaliar filtragem na borda.",
                    entries.size(),
                    maxKeys);
        }

        int size() {
            return entries.size();
        }

        /**
         * Mantem o mapa dentro do teto.
         *
         * <p>Remove primeiro o que expirou, que e o caso comum. Se ainda assim
         * estiver no teto, descarta as entradas mais antigas <b>entre as nao
         * bloqueadas</b>. Entradas bloqueadas nunca sao descartadas: sem essa
         * restricao, inundar o limitador apagaria o proprio bloqueio e devolveria
         * tentativas ao atacante.
         */
        private void contain(Instant now, Instant cutoff) {
            int currentSize = entries.size();

            if (currentSize < sweepThreshold) {
                return;
            }

            boolean overCap = currentSize >= maxKeys;

            // Fora do teto, a varredura e espacada no tempo. No teto ela ocorre
            // de imediato: o limite de memoria nao pode esperar.
            if (!overCap && lastSweepAt != null && lastSweepAt.isAfter(now.minus(SWEEP_INTERVAL))) {
                return;
            }

            // Uma limpeza por vez: varias threads varrendo o mapa ao mesmo
            // tempo apenas desperdiçariam CPU no pior momento possivel.
            if (!cleaning.compareAndSet(false, true)) {
                return;
            }

            try {
                lastSweepAt = now;
                entries.entrySet().removeIf(entry -> entry.getValue().lastAttempt().isBefore(cutoff));

                if (entries.size() < maxKeys) {
                    return;
                }

                // Reduz a 75% do teto para nao repetir o descarte a cada chamada.
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
