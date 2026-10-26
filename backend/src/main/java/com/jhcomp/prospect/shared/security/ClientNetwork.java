package com.jhcomp.prospect.shared.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Redes de origem de um endereco, em dois niveis, para fins de limite de
 * tentativas (D20).
 *
 * <p>Limitar por endereco exato nao funciona com IPv6: um assinante recebe um
 * bloco {@code /64} inteiro e troca de endereco dentro dele sem custo. Por
 * isso o IPv6 e agrupado em dois niveis:
 *
 * <ul>
 *   <li>{@code subscriber}: o {@code /64}, que corresponde a um assinante. Leva
 *       a cota normal, de modo que um assinante abusivo nao atinge os
 *       vizinhos.
 *   <li>{@code site}: o {@code /48}, alocacao tipica de um site. Quem tem um
 *       {@code /48} dispoe de 65.536 blocos {@code /64}; o teto agregado impede
 *       que ele multiplique cotas e entradas de rastreamento trocando de bloco.
 * </ul>
 *
 * <p>O IPv4 e exato nos dois niveis: blocos IPv4 sao escassos e agrupar por
 * {@code /24} atingiria usuarios legitimos sem relacao com o atacante.
 *
 * @param subscriber chave do assinante: IPv4 exato ou prefixo {@code /64}
 * @param site chave do site: IPv4 exato ou prefixo {@code /48}
 */
public record ClientNetwork(String subscriber, String site) {

    private static final int SUBSCRIBER_PREFIX_BYTES = 8;
    private static final int SITE_PREFIX_BYTES = 6;

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /**
     * Redes do endereco informado.
     *
     * <p>Nunca resolve nome: so interpreta literais de IP. Um valor que nao seja
     * literal e usado como veio nos dois niveis, para que uma entrada
     * desconhecida continue sendo contabilizada em vez de escapar do limite.
     */
    public static ClientNetwork of(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return exact("unknown");
        }

        String address = clientIp.strip().toLowerCase(Locale.ROOT);

        // Forma IPv4 mapeada em IPv6, comum em servidores com dual stack.
        if (address.startsWith("::ffff:") && IPV4_LITERAL.matcher(address.substring(7)).matches()) {
            return exact(address.substring(7));
        }

        if (IPV4_LITERAL.matcher(address).matches() || address.indexOf(':') < 0) {
            // IPv4, ou valor que nao e literal de IP. Nao se tenta resolver:
            // resolucao de nome numa rota publica permitiria induzir consultas
            // DNS externas.
            return exact(address);
        }

        return ipv6(address);
    }

    private static ClientNetwork exact(String address) {
        return new ClientNetwork(address, address);
    }

    private static ClientNetwork ipv6(String address) {
        byte[] bytes;
        try {
            // Literal IPv6: getByName nao faz consulta de nome neste caso.
            bytes = InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException exception) {
            return exact(address);
        }

        if (bytes.length != 16) {
            return exact(address);
        }

        return new ClientNetwork(prefix(bytes, SUBSCRIBER_PREFIX_BYTES), prefix(bytes, SITE_PREFIX_BYTES));
    }

    private static String prefix(byte[] bytes, int length) {
        return HexFormat.of().formatHex(bytes, 0, length) + "::/" + (length * 8);
    }
}
