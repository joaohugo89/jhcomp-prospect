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
 * Verificacao do site contra um servidor HTTP local (doc 06 §6, docs/07 §6).
 *
 * <p>O fetcher de producao bloqueia loopback; aqui ele usa a politica de teste,
 * que libera so loopback, e a porta do servidor local. O ultimo teste usa a
 * politica de producao e prova que o mesmo servidor e recusado.
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
    @DisplayName("le a pagina inicial e extrai so sinais e hash")
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
        // Servidor so HTTP: a tentativa HTTPS falhou.
        assertThat(observation.httpsAvailable()).isFalse();
        // robots.txt primeiro, pagina depois.
        assertThat(requested).containsSubsequence("/robots.txt", "/");
    }

    @Test
    @DisplayName("pagina sem viewport e sem link de contato")
    void missingSignals() {
        routes.put("/", exchange -> respond(exchange, 200, "text/html", "<html><body>Fale conosco pelo formulario</body></html>"));

        WebsiteObservation observation = inspector.inspect(base);

        assertThat(observation.hasViewport()).isFalse();
        assertThat(observation.hasContactLink()).isFalse();
    }

    @Test
    @DisplayName("robots.txt proibindo: a pagina inicial nunca e requisitada")
    void respectsRobots() {
        routes.put("/robots.txt", exchange -> respond(exchange, 200, "text/plain", "User-agent: *\nDisallow: /\n"));
        routes.put("/", exchange -> respond(exchange, 200, "text/html", "<html></html>"));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.ROBOTS_DISALLOWED);
        assertThat(requested).doesNotContain("/");
    }

    @Test
    @DisplayName("erro do servidor no robots.txt e tratado como proibicao (RFC 9309)")
    void robotsServerErrorDisallows() {
        routes.put("/robots.txt", exchange -> respond(exchange, 503, "text/plain", "indisponivel"));
        routes.put("/", exchange -> respond(exchange, 200, "text/html", "<html></html>"));

        assertThat(inspector.inspect(base).outcome()).isEqualTo(Outcome.ROBOTS_DISALLOWED);
        assertThat(requested).doesNotContain("/");
    }

    @Test
    @DisplayName("redirecionamentos alem do limite param a leitura")
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

    /** SSRF por redirecionamento: o site publico manda para a rede interna. */
    @Test
    @DisplayName("redirecionamento para rede interna e bloqueado")
    void redirectToInternalNetworkIsBlocked() {
        routes.put("/", redirect("http://169.254.169.254/latest/meta-data/"));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_UNUSABLE);
        assertThat(observation.detail()).isEqualTo("BLOCKED_ADDRESS");
    }

    @Test
    @DisplayName("redirecionamento para esquema que nao e web e recusado")
    void redirectToOtherSchemeIsRefused() {
        routes.put("/", redirect("file:///etc/passwd"));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_UNUSABLE);
        assertThat(observation.detail()).isEqualTo("INVALID_URL");
    }

    @Test
    @DisplayName("conteudo que nao e HTML nao e lido")
    void nonHtml() {
        routes.put("/", exchange -> respond(exchange, 200, "application/pdf", "%PDF-1.4"));

        WebsiteObservation observation = inspector.inspect(base + "/");

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_UNUSABLE);
        assertThat(observation.detail()).isEqualTo("NOT_HTML");
    }

    /**
     * Corpo acima do limite nao e lido ate o fim.
     *
     * <p>O servidor manda mais que o limite e continua pingando bytes devagar.
     * Se o fetcher consumisse o restante ao fechar, o teste esperaria os 30 s
     * do servidor; validado ao contrario, consumindo o corpo antes de fechar.
     */
    @Test
    @DisplayName("corpo grande e lento nao prende o worker")
    void oversizedBodyIsCutShort() {
        routes.put("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(new byte[WebsiteInspector.MAX_PAGE_BYTES + 1024]);
                output.flush();
                // Depois do limite, um byte a cada 100 ms por 30 s: nunca
                // estoura o timeout de leitura. Quem drenasse o corpo ate o fim
                // ficaria preso aqui.
                for (int i = 0; i < 300; i++) {
                    output.write('x');
                    output.flush();
                    Thread.sleep(Duration.ofMillis(100));
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                // O cliente fechou a conexao: e o comportamento esperado.
            }
        });

        long started = System.nanoTime();
        WebsiteObservation observation = inspector.inspect(base + "/");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(observation.outcome()).isEqualTo(Outcome.PAGE_READ);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("politica de producao recusa o mesmo servidor local")
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
    @DisplayName("porta fora de 80 e 443 nao e acessada")
    void nonStandardPortIsBlocked() {
        var production = new WebsiteInspector(CLOCK);

        WebsiteObservation observation = production.inspect("http://exemplo.test:8080/");

        assertThat(observation.outcome()).isEqualTo(Outcome.UNREACHABLE);
        assertThat(observation.detail()).isEqualTo("BLOCKED_PORT");
    }

    @Test
    @DisplayName("sem site cadastrado, nada e acessado")
    void noWebsite() {
        assertThat(inspector.inspect("  ").outcome()).isEqualTo(Outcome.NO_WEBSITE);
        assertThat(requested).isEmpty();
    }
}
