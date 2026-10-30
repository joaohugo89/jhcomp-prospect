package com.jhcomp.prospect.analysis.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prazo total do fetcher contra um servidor que responde devagar de proposito.
 *
 * <p>Socket cru, e nao HttpServer: o caso testado e o de cabecalhos enviados
 * um byte por vez, o que um servidor HTTP comum nao faz.
 */
@DisplayName("SafeHttpFetcher")
class SafeHttpFetcherTest {

    private ServerSocket server;
    private Thread acceptor;

    @BeforeEach
    void start() throws IOException {
        server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        acceptor = Thread.ofVirtual().start(() -> {
            while (!server.isClosed()) {
                try {
                    Socket socket = server.accept();
                    Thread.ofVirtual().start(() -> trickleHeaders(socket));
                } catch (IOException exception) {
                    return;
                }
            }
        });
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
        acceptor.interrupt();
    }

    /** Linha de status e depois um byte de cabecalho a cada 100 ms, por 30 s. */
    private static void trickleHeaders(Socket socket) {
        try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            in.read(new byte[1024]);
            out.write("HTTP/1.1 200 OK\r\nX-Lento: ".getBytes(StandardCharsets.US_ASCII));
            for (int i = 0; i < 300; i++) {
                out.write('a');
                out.flush();
                Thread.sleep(100);
            }
        } catch (IOException exception) {
            // Cliente desistiu: e o comportamento esperado.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cada byte reinicia o timeout de leitura, que por isso nunca estoura; so
     * o prazo total encerra a requisicao. Validado ao contrario: sem o
     * cancelamento agendado, o teste espera os 30 s do servidor.
     */
    @Test
    @DisplayName("cabecalhos enviados devagar esgotam o prazo total")
    void slowHeadersHitDeadline() throws Exception {
        int port = server.getLocalPort();
        try (var fetcher = new SafeHttpFetcher(AddressPolicy.allowingLoopbackForTests(), Set.of(port), Duration.ofMillis(300))) {
            long started = System.nanoTime();
            FetchException failure = catchThrowableOfType(FetchException.class,
                    () -> fetcher.get(URI.create("http://127.0.0.1:" + port + "/"), 1024));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertThat(failure).describedAs("requisicao deveria falhar por prazo").isNotNull();
            assertThat(failure.code()).isEqualTo("TIMEOUT");
            assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        }
    }
}
