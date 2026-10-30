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
 * Cliente HTTP protegido contra SSRF (docs/07 §6, regra 8 do produto).
 *
 * <p>Protecoes:
 *
 * <ul>
 *   <li>So {@code http}/{@code https}, nas portas padrao. Porta arbitraria
 *       serviria para sondar servicos internos ou de terceiros.
 *   <li>O endereco e validado pelo {@link DnsResolver} usado na propria
 *       conexao. Validar o nome antes e conectar depois abriria DNS rebinding:
 *       a segunda resolucao poderia devolver um IP interno.
 *   <li>Sem redirecionamento automatico: quem chama decide se segue, e cada
 *       destino passa pelas mesmas regras.
 *   <li>Timeout de conexao e de leitura, prazo total por requisicao e limite
 *       de bytes lidos: um servidor lento ou enorme nao prende o worker.
 *   <li>Sem cookies, sem autenticacao, sem proxy do sistema.
 * </ul>
 */
public class SafeHttpFetcher implements AutoCloseable {

    public static final String USER_AGENT = "JHCOMP-Prospect/1.0 (verificacao de presenca digital)";


    /** Cabecalho muito longo ou numeroso e recusado, sem consumir memoria. */
    private static final Http1Config HTTP1_LIMITS = Http1Config.custom()
            .setMaxLineLength(8 * 1024)
            .setMaxHeaderCount(100)
            .build();

    /** Cancela requisicoes que passam do prazo total. Uma thread daemon basta. */
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "safe-http-fetcher-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final CloseableHttpClient client;
    private final Set<Integer> allowedPorts;
    /** Prazo total por requisicao: tres vezes o timeout de conexao (15 s em producao). */
    private final Duration deadline;

    /** Resposta HTTP. {@code body} vazio quando o conteudo nao e texto. */
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

    /** Endereco bloqueado pela politica; distinto de nome inexistente. */
    private static final class BlockedAddressException extends UnknownHostException {
        private static final long serialVersionUID = 1L;
    }

    public SafeHttpFetcher(AddressPolicy policy) {
        this(policy, Set.of(), Duration.ofSeconds(5));
    }

    /**
     * Somente para teste: libera, alem de 80 e 443, a porta do servidor local
     * e encurta a espera de conexao, que contra um servidor so HTTP faria cada
     * tentativa HTTPS esperar o timeout inteiro.
     */
    SafeHttpFetcher(AddressPolicy policy, Set<Integer> extraPorts, Duration connectTimeout) {
        this.allowedPorts = extraPorts.isEmpty() ? Set.of(80, 443) : union(extraPorts);
        this.deadline = connectTimeout.multipliedBy(3);
        DnsResolver validatingResolver = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
                // Um unico endereco bloqueado recusa o host inteiro: escolher o
                // "bom" deixaria a tentativa seguinte cair no ruim.
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
     * GET sem seguir redirecionamento.
     *
     * @param maxBytes teto de bytes lidos do corpo; o excedente e descartado
     * @throws FetchException quando nao ha resposta HTTP
     */
    public Response get(URI uri, int maxBytes) throws FetchException {
        validate(uri);
        long deadlineNanos = System.nanoTime() + deadline.toNanos();
        // Fechar a resposta drena o restante do corpo, e um servidor que manda
        // mais que o limite e continua pingando bytes nunca estoura o timeout
        // de leitura: prenderia o worker. Cancelar a requisicao antes de fechar
        // aborta a conexao em vez de drenar (oversizedBodyIsCutShort).
        HttpGet request = new HttpGet(uri);
        // Prazo total da troca inteira, cabecalhos incluidos: o timeout de
        // leitura reinicia a cada byte, e um servidor que manda cabecalhos um
        // byte por vez escaparia dele.
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
            // Cancelada pelo prazo total ou estouro de timeout de leitura.
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
        HttpEntity entity = response.getEntity();
        ContentType type = entity == null || entity.getContentType() == null
                ? null
                : ContentType.parseLenient(entity.getContentType());
        String mime = type == null ? null : type.getMimeType();
        boolean text = mime != null && mime.toLowerCase(Locale.ROOT).startsWith("text/");
        byte[] body = text ? readLimited(entity.getContent(), maxBytes, deadline) : new byte[0];
        Charset charset = type != null && type.getCharset() != null ? type.getCharset() : StandardCharsets.UTF_8;
        return new Response(response.getCode(), location == null ? null : location.getValue(), mime, body, charset);
    }

    /**
     * Fecha a resposta de uma requisicao ja cancelada. O erro de fechar a
     * conexao abortada de proposito nao e falha da verificacao: propaga-lo
     * substituiria a resposta ja lida por uma falha de conexao.
     */
    private static void closeDiscarding(ClassicHttpResponse response) {
        try {
            response.close();
        } catch (IOException exception) {
            // Conexao abortada pelo cancel(); a resposta ja foi lida.
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

    /** Le ate {@code maxBytes}, abortando se o prazo total esgotar. */
    private static byte[] readLimited(InputStream input, int maxBytes, long deadline) throws IOException {
        byte[] buffer = new byte[Math.min(maxBytes, 64 * 1024)];
        var output = new ByteArrayOutputStream();
        int read;
        while (output.size() < maxBytes && (read = input.read(buffer, 0, Math.min(buffer.length, maxBytes - output.size()))) != -1) {
            output.write(buffer, 0, read);
            // Servidor que manda um byte por vez nunca dispara o timeout de
            // leitura; o prazo total cobre esse caso.
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
