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
 * The fetcher's total deadline against a server that answers slowly on
 * purpose.
 *
 * <p>A raw socket rather than HttpServer: the case under test is headers sent
 * one byte at a time, which an ordinary HTTP server does not do.
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

    /** A status line, then one header byte every 100 ms, for 30 s. */
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
            // The client gave up: that is the expected behaviour.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Every byte restarts the read timeout, which therefore never trips; only
     * the total deadline ends the request. Validated in reverse: without the
     * scheduled cancellation, the test waits out the server's 30 s.
     */
    @Test
    @DisplayName("headers sent slowly exhaust the total deadline")
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
