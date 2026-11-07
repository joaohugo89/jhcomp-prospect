package com.jhcomp.prospect.analysis.infrastructure.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

/**
 * An HTTP client hardened against SSRF (docs/07 §6, product rule 8).
 *
 * <p>Protections:
 *
 * <ul>
 *   <li>{@code http}/{@code https} only, on the standard ports. An arbitrary
 *       port would serve to probe internal or third-party services.
 *   <li>The address is validated by the {@link DnsResolver} used by the
 *       connection itself. Validating the name first and connecting afterwards
 *       would open DNS rebinding: the second resolution could return an
 *       internal IP.
 *   <li>No automatic redirects: the caller decides whether to follow, and
 *       every destination goes through the same rules.
 *   <li>Connection and read timeouts, a total deadline per request, and a cap
 *       on bytes read: a slow or enormous server cannot hold the worker.
 *   <li>No cookies, no authentication, no system proxy.
 * </ul>
 */
public class SafeHttpFetcher implements AutoCloseable {

    public static final String USER_AGENT = "JHCOMP-Prospect/1.0 (verificacao de presenca digital)";


    /** A header that is too long, or too numerous, is refused without consuming memory. */
    private static final Http1Config HTTP1_LIMITS = Http1Config.custom()
            .setMaxLineLength(8 * 1024)
            .setMaxHeaderCount(100)
            .build();

    /** Cancels requests that exceed the total deadline. One daemon thread is enough. */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "safe-http-fetcher-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final CloseableHttpClient client;
    private final Set<Integer> allowedPorts;
    /** Total deadline per request: three times the connection timeout (15 s in production). */
    private final Duration deadline;

    /** An HTTP response. {@code body} is empty when the content is not text. */
    public record Response(int status, String location, String contentType, byte[] body, Charset charset) {

        public boolean isRedirect() {
            return status >= 300 && status < 400 && location != null;
        }

        public boolean isHtml() {
            return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/html");
        }

        public String text() {
            return new String(body, charset);
        }
    }

    /** An address blocked by policy; distinct from a name that does not resolve. */
    private static final class BlockedAddressException extends UnknownHostException {
        private static final long serialVersionUID = 1L;
    }

    public SafeHttpFetcher(AddressPolicy policy) {
        this(policy, Set.of(), Duration.ofSeconds(5));
    }

    /**
     * For tests only: besides 80 and 443 it allows the local server's port,
     * and shortens the connection wait, which against an HTTP-only server
     * would make every HTTPS attempt wait out the full timeout.
     */
    SafeHttpFetcher(AddressPolicy policy, Set<Integer> extraPorts, Duration connectTimeout) {
        this.allowedPorts = extraPorts.isEmpty() ? Set.of(80, 443) : union(extraPorts);
        this.deadline = connectTimeout.multipliedBy(3);
        DnsResolver validatingResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
                // A single blocked address refuses the whole host: picking the
                // "good" one would let the next attempt land on the bad one.
                for (InetAddress address : addresses) {
                    if (policy.isBlocked(address)) {
                        throw new BlockedAddressException();
                    }
                }
                return addresses;
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
            }
        };

        this.client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(validatingResolver)
                        .setConnectionFactory(ManagedHttpClientConnectionFactory.builder()
                                .http1Config(HTTP1_LIMITS)
                                .build())
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(connectTimeout))
                                .setSocketTimeout(Timeout.of(connectTimeout.multipliedBy(2)))
                                .build())
                        .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(Timeout.of(connectTimeout.multipliedBy(2))).build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(connectTimeout.multipliedBy(2)))
                        .setRedirectsEnabled(false)
                        .build())
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .setUserAgent(USER_AGENT)
                .build();
    }

    /**
     * A GET that does not follow redirects.
     *
     * @param maxBytes cap on bytes read from the body; the excess is discarded
     * @throws FetchException when there is no HTTP response
     */
    public Response get(URI uri, int maxBytes) throws FetchException {
        validate(uri);
        long deadlineNanos = System.nanoTime() + deadline.toNanos();
        // Closing the response drains the rest of the body, and a server that
        // sends more than the limit and keeps dripping bytes never trips the
        // read timeout: it would hold the worker. Cancelling the request before
        // closing aborts the connection instead of draining it
        // (oversizedBodyIsCutShort).
        HttpGet request = new HttpGet(uri);
        // A deadline for the whole exchange, headers included: the read
        // timeout restarts on every byte, and a server sending headers one byte
        // at a time would slip past it.
        ScheduledFuture<?> watchdog = WATCHDOG.schedule(request::cancel, deadline.toMillis(), TimeUnit.MILLISECONDS);
        try {
            ClassicHttpResponse response = client.executeOpen(null, request, null);
            try {
                return read(response, maxBytes, deadlineNanos);
            } finally {
                request.cancel();
                closeDiscarding(response);
            }
        } catch (BlockedAddressException exception) {
            throw new FetchException("BLOCKED_ADDRESS");
        } catch (UnknownHostException exception) {
            throw new FetchException("DNS");
        } catch (IOException exception) {
            // Cancelled by the total deadline, or the read timeout expired.
            if (exception instanceof InterruptedIOException || watchdog.isDone()) {
                throw new FetchException("TIMEOUT");
            }
            throw new FetchException("CONNECTION");
        } finally {
            watchdog.cancel(false);
        }
    }

    private static Response read(ClassicHttpResponse response, int maxBytes, long deadline) throws IOException {
        Header location = response.getFirstHeader("Location");
        String locationValue = location == null ? null : location.getValue();
        HttpEntity entity = response.getEntity();
        ContentType type = entity == null ? null : ContentType.parseLenient(entity.getContentType());
        if (entity == null || type == null) {
            return new Response(response.getCode(), locationValue, null, new byte[0], StandardCharsets.UTF_8);
        }
        String mime = type.getMimeType();
        boolean text = mime != null && mime.toLowerCase(Locale.ROOT).startsWith("text/");
        byte[] body = text ? readLimited(entity.getContent(), maxBytes, deadline) : new byte[0];
        Charset charset = type.getCharset() != null ? type.getCharset() : StandardCharsets.UTF_8;
        return new Response(response.getCode(), locationValue, mime, body, charset);
    }

    /**
     * Closes the response of an already cancelled request. The error from
     * closing a deliberately aborted connection is not a verification failure:
     * propagating it would replace the response already read with a connection
     * error.
     */
    private static void closeDiscarding(ClassicHttpResponse response) {
        try {
            response.close();
        } catch (IOException exception) {
            // The connection was aborted by cancel(); the response is already read.
        }
    }

    private void validate(URI uri) throws FetchException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean webScheme = scheme.equals("http") || scheme.equals("https");
        if (!webScheme || uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new FetchException("INVALID_URL");
        }
        int port = uri.getPort();
        if (port != -1 && !allowedPorts.contains(port)) {
            throw new FetchException("BLOCKED_PORT");
        }
    }

    private static Set<Integer> union(Set<Integer> extraPorts) {
        Set<Integer> ports = new HashSet<>(extraPorts);
        ports.add(80);
        ports.add(443);
        return Set.copyOf(ports);
    }

    /** Reads up to {@code maxBytes}, aborting if the total deadline runs out. */
    private static byte[] readLimited(InputStream input, int maxBytes, long deadline) throws IOException {
        byte[] buffer = new byte[Math.min(maxBytes, 64 * 1024)];
        var output = new ByteArrayOutputStream();
        int read;
        while (output.size() < maxBytes && (read = input.read(buffer, 0, Math.min(buffer.length, maxBytes - output.size()))) != -1) {
            output.write(buffer, 0, read);
            // A server sending one byte at a time never trips the read
            // timeout; the total deadline covers that case.
            if (System.nanoTime() > deadline) {
                throw new InterruptedIOException("deadline");
            }
        }
        return output.toByteArray();
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
