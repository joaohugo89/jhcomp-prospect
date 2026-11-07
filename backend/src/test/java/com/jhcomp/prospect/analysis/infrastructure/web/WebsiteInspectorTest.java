package com.jhcomp.prospect.analysis.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jhcomp.prospect.analysis.domain.WebsiteObservation;
import com.jhcomp.prospect.analysis.domain.WebsiteObservation.Outcome;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Website verification against a local HTTP server (doc 06 §6, docs/07 §6).
 *
 * <p>The production fetcher blocks loopback; here it uses the test policy,
 * which allows loopback only, and the local server's port. The last test uses
 * the production policy and proves that the same server is refused.
 */
@DisplayName("WebsiteInspector")
class WebsiteInspectorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);

    private interface Route {
        void handle(HttpExchange exchange) throws IOException;
    }

    private HttpServer server;
    private final Map<String, Route> routes = new ConcurrentHashMap<>();
    private final List<String> requested = new CopyOnWriteArrayList<>();
    private WebsiteInspector inspector;
    private String base;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requested.add(exchange.getRequestURI().getPath());
            Route route = routes.get(exchange.getRequestURI().getPath());
            if (route == null) {
                respond(exchange, 404, "text/plain", "");
            } else {
                route.handle(exchange);
            }
            exchange.close();
        });
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        int port = server.getAddress().getPort();
        base = "http://127.0.0.1:" + port;
        inspector = new WebsiteInspector(
                new SafeHttpFetcher(AddressPolicy.allowingLoopbackForTests(), Set.of(port), Duration.ofMillis(300)), CLOCK);
    }

    @AfterEach
    void stop() throws Exception {
        server.stop(0);
        inspector.destroy();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    private static Route redirect(String location) {
        return exchange -> {
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(302, -1);
        };
    }

    @Test
    @DisplayName("reads the home page and extracts only signals and a hash")
    void readsHomePage() {
        routes.put("/", exchange -> respond(exchange, 200, "text/html; charset=utf-8", """
                <html><head><meta charset="utf-8"><meta content="width=device-width" name="viewport"></head>
                <body><a href="tel:+5535999990000">Ligue</a></body></html>
                """));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_READ);
        assertThat(observation.hasViewport()).isTrue();
        assertThat(observation.hasContactLink()).isTrue();
        assertThat(observation.contentHash()).hasSize(64);
        // An HTTP-only server: the HTTPS attempt failed.
        assertThat(observation.httpsAvailable()).isFalse();
        // robots.txt first, the page afterwards.
        assertThat(requested).containsSubsequence("/robots.txt", "/");
    }

    @Test
    @DisplayName("a page with no viewport and no contact link")
    void missingSignals() {
        routes.put("/", exchange -> respond(exchange, 200, "text/html", "<html><body>Fale conosco pelo formulario</body></html>"));

        WebsiteObservation observation = inspector.inspect(base);

        assertThat(observation.hasViewport()).isFalse();
        assertThat(observation.hasContactLink()).isFalse();
    }

    @Test
    @DisplayName("robots.txt forbidding: the home page is never requested")
    void respectsRobots() {
        routes.put("/robots.txt", exchange -> respond(exchange, 200, "text/plain", "User-agent: *\nDisallow: /\n"));
        routes.put("/", exchange -> respond(exchange, 200, "text/html", "<html></html>"));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.ROBOTS_DISALLOWED);
        assertThat(requested).doesNotContain("/");
    }

    @Test
    @DisplayName("a server error on robots.txt is treated as a prohibition (RFC 9309)")
    void robotsServerErrorDisallows() {
        routes.put("/robots.txt", exchange -> respond(exchange, 503, "text/plain", "indisponivel"));
        routes.put("/", exchange -> respond(exchange, 200, "text/html", "<html></html>"));

        assertThat(inspector.inspect(base).outcome()).isEqualTo(Outcome.ROBOTS_DISALLOWED);
        assertThat(requested).doesNotContain("/");
    }

    @Test
    @DisplayName("redirects beyond the limit stop the read")
    void redirectLimit() {
        routes.put("/", redirect("/a"));
        routes.put("/a", redirect("/b"));
        routes.put("/b", redirect("/c"));
        routes.put("/c", redirect("/d"));
        routes.put("/d", exchange -> respond(exchange, 200, "text/html", "<html></html>"));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_UNUSABLE);
        assertThat(observation.detail()).isEqualTo("TOO_MANY_REDIRECTS");
        assertThat(requested).doesNotContain("/d");
    }

    /** SSRF by redirect: the public site sends you to the internal network. */
    @Test
    @DisplayName("a redirect to the internal network is blocked")
    void redirectToInternalNetworkIsBlocked() {
        routes.put("/", redirect("http://169.254.169.254/latest/meta-data/"));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_UNUSABLE);
        assertThat(observation.detail()).isEqualTo("BLOCKED_ADDRESS");
    }

    @Test
    @DisplayName("a redirect to a non-web scheme is refused")
    void redirectToOtherSchemeIsRefused() {
        routes.put("/", redirect("file:///etc/passwd"));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_UNUSABLE);
        assertThat(observation.detail()).isEqualTo("INVALID_URL");
    }

    @Test
    @DisplayName("content that is not HTML is not read")
    void nonHtml() {
        routes.put("/", exchange -> respond(exchange, 200, "application/pdf", "%PDF-1.4"));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_UNUSABLE);
        assertThat(observation.detail()).isEqualTo("NOT_HTML");
    }

    /**
     * A body over the limit is not read to the end.
     *
     * <p>The server sends more than the limit and keeps dripping bytes slowly.
     * If the fetcher consumed the rest on close, the test would wait out the
     * server's 30 s; validated in reverse, by consuming the body before
     * closing.
     */
    @Test
    @DisplayName("a large, slow body does not hold the worker")
    void oversizedBodyIsCutShort() {
        routes.put("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(new byte[WebsiteInspector.MAX_PAGE_BYTES + 1024]);
                output.flush();
                // Past the limit, one byte every 100 ms for 30 s: it never
                // trips the read timeout. Anything draining the body to the end
                // would be stuck here.
                for (int i = 0; i < 300; i++) {
                    output.write('x');
                    output.flush();
                    Thread.sleep(Duration.ofMillis(100));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                // The client closed the connection: that is the expected behaviour.
            }
        });

        long started = System.nanoTime();
        WebsiteObservation observation = inspector.inspect(base + "/");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_READ);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("the production policy refuses the very same local server")
    void productionPolicyBlocksLoopback() throws Exception {
        int port = server.getAddress().getPort();
        try (var fetcher = new SafeHttpFetcher(AddressPolicy.PUBLIC_ONLY, Set.of(port), Duration.ofMillis(300))) {
            WebsiteObservation observation = new WebsiteInspector(fetcher, CLOCK).inspect(base + "/");

            assertThat(observation.outcome()).isEqualTo(Outcome.UNREACHABLE);
            assertThat(observation.detail()).isEqualTo("BLOCKED_ADDRESS");
        }
        assertThat(requested).isEmpty();
    }

    @Test
    @DisplayName("a port other than 80 and 443 is not accessed")
    void nonStandardPortIsBlocked() {
        var production = new WebsiteInspector(CLOCK);

        WebsiteObservation observation = production.inspect("http://exemplo.test:8080/");

        assertThat(observation.outcome()).isEqualTo(Outcome.UNREACHABLE);
        assertThat(observation.detail()).isEqualTo("BLOCKED_PORT");
    }

    @Test
    @DisplayName("with no website on file, nothing is accessed")
    void noWebsite() {
        assertThat(inspector.inspect("  ").outcome()).isEqualTo(Outcome.NO_WEBSITE);
        assertThat(requested).isEmpty();
    }
}
