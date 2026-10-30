package com.jhcomp.prospect.analysis.infrastructure.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jhcomp.prospect.analysis.application.WebsiteChecker;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation.Outcome;

/**
 * Verificacao do site: robots.txt, HTTPS e sinais da pagina inicial (doc 06 §6).
 *
 * <p>Ordem: primeiro o robots.txt, que tambem diz se o site responde e por
 * qual esquema; so depois a pagina inicial, se o robots.txt permitir. Nada
 * alem dela e lido: a analise nao navega pelo site.
 *
 * <p>Conteudo da pagina e dado nao confiavel (regra 9 do produto). Dele saem
 * apenas sinais booleanos por expressao regular e um hash; o HTML nao e
 * guardado, interpretado como instrucao nem executado.
 */
@Component
public class WebsiteInspector implements WebsiteChecker, DisposableBean {

    static final int MAX_REDIRECTS = 3;

    /**
     * Prazo total da verificacao de um site. Cada requisicao ja tem o proprio
     * prazo, mas robots.txt, HTTP e redirecionamentos somam varias: sem este
     * teto, um site lento ocuparia o worker por minutos.
     */
    static final Duration TOTAL_DEADLINE = Duration.ofSeconds(45);
    static final int MAX_PAGE_BYTES = 1024 * 1024;
    private static final int MAX_ROBOTS_BYTES = 256 * 1024;

    private static final Pattern VIEWPORT = Pattern.compile(
            "<meta\\b[^>]*\\bname\\s*=\\s*[\"']?viewport\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern CONTACT_LINK = Pattern.compile(
            "\\bhref\\s*=\\s*[\"']?\\s*(?:tel:|mailto:|https?://(?:wa\\.me/|api\\.whatsapp\\.com/|web\\.whatsapp\\.com/"
                    + "|(?:www\\.)?whatsapp\\.com/send))",
            Pattern.CASE_INSENSITIVE);

    private final SafeHttpFetcher fetcher;
    private final Clock clock;

    @Autowired
    WebsiteInspector(Clock clock) {
        this(new SafeHttpFetcher(AddressPolicy.PUBLIC_ONLY), clock);
    }

    WebsiteInspector(SafeHttpFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    @Override
    public WebsiteObservation inspect(String websiteUrl) {
        deadline.set(System.nanoTime() + TOTAL_DEADLINE.toNanos());
        try {
            return inspectWithinDeadline(websiteUrl);
        } finally {
            deadline.remove();
        }
    }

    /** Prazo da verificacao em curso, por thread: o bean e compartilhado. */
    private final ThreadLocal<Long> deadline = new ThreadLocal<>();

    private SafeHttpFetcher.Response fetch(URI uri, int maxBytes) throws FetchException {
        Long limit = deadline.get();
        if (limit != null && System.nanoTime() > limit) {
            throw new FetchException("TIMEOUT");
        }
        return fetcher.get(uri, maxBytes);
    }

    private WebsiteObservation inspectWithinDeadline(String websiteUrl) {
        Instant now = clock.instant();
        if (websiteUrl == null || websiteUrl.isBlank()) {
            return WebsiteObservation.noWebsite(now);
        }

        URI start;
        try {
            start = toUri(websiteUrl.strip());
        } catch (URISyntaxException exception) {
            return unreachable(websiteUrl, "INVALID_URL", now);
        }
        String requested = start.toString();

        // 1. robots.txt por HTTPS; sem resposta, por HTTP. A resposta, de
        // qualquer status, ja prova que o site existe e por qual esquema.
        RobotsAttempt https = fetchRobots("https", start.getHost(), start.getPort());
        RobotsAttempt robots = https;
        if (https.failure() != null) {
            if (isFinal(https.failure())) {
                return unreachable(requested, https.failure(), now);
            }
            robots = fetchRobots("http", start.getHost(), start.getPort());
            if (robots.failure() != null) {
                return unreachable(requested, https.failure(), now);
            }
        }
        boolean httpsAvailable = https.failure() == null;
        String path = pathOf(start);

        if (!robots.rules().allows(path)) {
            return new WebsiteObservation(Outcome.ROBOTS_DISALLOWED, requested, null, httpsAvailable, null,
                    false, false, null, null, now);
        }

        // 2. Pagina inicial, seguindo redirecionamentos validados um a um.
        URI current = withScheme(start, httpsAvailable ? "https" : "http");
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            SafeHttpFetcher.Response response;
            try {
                response = fetch(current, MAX_PAGE_BYTES);
            } catch (FetchException exception) {
                return unusable(requested, current, httpsAvailable, null, exception.code(), now);
            }

            if (response.isRedirect()) {
                Optional<URI> next = resolveRedirect(current, response.location());
                if (next.isEmpty()) {
                    return unusable(requested, current, httpsAvailable, response.status(), "INVALID_URL", now);
                }
                URI target = next.get();
                httpsAvailable = httpsAvailable || "https".equals(target.getScheme());
                // Outro host tem outro robots.txt, que tambem e respeitado. Sem
                // resposta dele (inclusive endereco bloqueado), o destino nao e lido.
                if (!target.getHost().equalsIgnoreCase(current.getHost())) {
                    RobotsAttempt targetRobots = fetchRobots(target.getScheme(), target.getHost(), target.getPort());
                    if (targetRobots.failure() != null) {
                        return unusable(requested, target, httpsAvailable, response.status(), targetRobots.failure(), now);
                    }
                    if (!targetRobots.rules().allows(pathOf(target))) {
                        return new WebsiteObservation(Outcome.ROBOTS_DISALLOWED, requested, target.toString(),
                                httpsAvailable, response.status(), false, false, null, null, now);
                    }
                }
                current = target;
                continue;
            }

            if (response.status() >= 400) {
                return unusable(requested, current, httpsAvailable, response.status(), null, now);
            }
            if (!response.isHtml()) {
                return unusable(requested, current, httpsAvailable, response.status(), "NOT_HTML", now);
            }

            String html = response.text();
            return new WebsiteObservation(
                    Outcome.PAGE_READ,
                    requested,
                    current.toString(),
                    httpsAvailable || "https".equals(current.getScheme()),
                    response.status(),
                    VIEWPORT.matcher(html).find(),
                    CONTACT_LINK.matcher(html).find(),
                    sha256(response.body()),
                    null,
                    now);
        }
        return unusable(requested, current, httpsAvailable, null, "TOO_MANY_REDIRECTS", now);
    }

    private record RobotsAttempt(RobotsRules rules, String failure) {}

    /**
     * robots.txt de um host. 4xx libera, 5xx proibe (RFC 9309); sem resposta,
     * devolve o motivo e nenhuma regra.
     */
    private RobotsAttempt fetchRobots(String scheme, String host, int port) {
        // robots.txt vale por autoridade: host e porta.
        URI uri = URI.create(scheme + "://" + host + (port == -1 ? "" : ":" + port) + "/robots.txt");
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            SafeHttpFetcher.Response response;
            try {
                response = fetch(uri, MAX_ROBOTS_BYTES);
            } catch (FetchException exception) {
                return new RobotsAttempt(RobotsRules.disallowAll(), exception.code());
            }
            if (response.isRedirect()) {
                Optional<URI> next = resolveRedirect(uri, response.location());
                if (next.isEmpty()) {
                    return new RobotsAttempt(RobotsRules.allowAll(), null);
                }
                uri = next.get();
                continue;
            }
            if (response.status() >= 500) {
                return new RobotsAttempt(RobotsRules.disallowAll(), null);
            }
            if (response.status() >= 400) {
                return new RobotsAttempt(RobotsRules.allowAll(), null);
            }
            return new RobotsAttempt(RobotsRules.parse(response.text()), null);
        }
        // Redirecionamento demais no robots.txt: tratado como inexistente.
        return new RobotsAttempt(RobotsRules.allowAll(), null);
    }

    /** Falhas que tentar por HTTP nao muda: o nome nao existe ou o endereco e proibido. */
    private static boolean isFinal(String failure) {
        return failure.equals("DNS") || failure.equals("BLOCKED_ADDRESS") || failure.equals("INVALID_URL");
    }

    private static Optional<URI> resolveRedirect(URI from, String location) {
        try {
            URI target = from.resolve(new URI(location.strip()));
            String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(Locale.ROOT);
            boolean webScheme = scheme.equals("http") || scheme.equals("https");
            if (!webScheme || target.getHost() == null) {
                return Optional.empty();
            }
            return Optional.of(target);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static URI toUri(String url) throws URISyntaxException {
        URI uri = new URI(url.contains("://") ? url : "https://" + url);
        if (uri.getHost() == null) {
            throw new URISyntaxException(url, "no host");
        }
        return uri;
    }

    private static URI withScheme(URI uri, String scheme) {
        try {
            return new URI(scheme, null, uri.getHost(), uri.getPort(), pathOf(uri), uri.getQuery(), null);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String pathOf(URI uri) {
        String path = uri.getPath();
        return path == null || path.isEmpty() ? "/" : path;
    }

    private static WebsiteObservation unreachable(String requested, String detail, Instant now) {
        return new WebsiteObservation(Outcome.UNREACHABLE, requested, null, null, null, false, false, null, detail, now);
    }

    private static WebsiteObservation unusable(
            String requested, URI current, boolean httpsAvailable, Integer status, String detail, Instant now) {
        return new WebsiteObservation(Outcome.PAGE_UNUSABLE, requested, current.toString(), httpsAvailable, status,
                false, false, null, detail, now);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in every JVM", exception);
        }
    }

    @Override
    public void destroy() throws Exception {
        fetcher.close();
    }
}
