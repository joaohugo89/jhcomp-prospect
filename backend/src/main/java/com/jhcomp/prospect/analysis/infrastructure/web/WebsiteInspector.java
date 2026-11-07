package com.jhcomp.prospect.analysis.infrastructure.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jhcomp.prospect.analysis.application.WebsiteChecker;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation.Outcome;
import com.jhcomp.prospect.shared.text.Hashes;

/**
 * Website verification: robots.txt, HTTPS and home page signals (doc 06 §6).
 *
 * <p>Order: robots.txt first, which also says whether the site answers and
 * under which scheme; only then the home page, if robots.txt allows it.
 * Nothing beyond it is read: the analysis does not browse the site.
 *
 * <p>Page content is untrusted data (product rule 9). Only boolean signals
 * matched by regular expression and a hash come out of it; the HTML is neither
 * stored, nor interpreted as an instruction, nor executed.
 */
@Component
public class WebsiteInspector implements WebsiteChecker, DisposableBean {

    static final int MAX_REDIRECTS = 3;

    /**
     * The total deadline for verifying one site. Each request already has its
     * own, but robots.txt, HTTP and redirects add up to several: without this
     * ceiling, a slow site would occupy the worker for minutes.
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

    /** The deadline of the verification in progress, per thread: the bean is shared. */
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

        // 1. robots.txt over HTTPS; with no answer, over HTTP. The response,
        // whatever its status, already proves the site exists and under which
        // scheme.
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

        // 2. The home page, following redirects validated one by one.
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
                // Another host has another robots.txt, which is respected too.
                // With no answer from it — a blocked address included — the
                // destination is not read.
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
                    Hashes.sha256Hex(response.body()),
                    null,
                    now);
        }
        return unusable(requested, current, httpsAvailable, null, "TOO_MANY_REDIRECTS", now);
    }

    private record RobotsAttempt(RobotsRules rules, String failure) {}

    /**
     * A host's robots.txt. 4xx allows, 5xx forbids (RFC 9309); with no answer
     * it returns the reason and no rules.
     */
    private RobotsAttempt fetchRobots(String scheme, String host, int port) {
        // robots.txt applies per authority: host and port.
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
        // Too many redirects on robots.txt: treated as if it did not exist.
        return new RobotsAttempt(RobotsRules.allowAll(), null);
    }

    /** Failures that retrying over HTTP cannot change: the name does not exist, or the address is forbidden. */
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

    @Override
    public void destroy() throws Exception {
        fetcher.close();
    }
}
