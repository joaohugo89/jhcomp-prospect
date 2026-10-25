package com.jhcomp.prospect.shared.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Agrupa enderecos de origem pela rede a que pertencem, para fins de limite de
 * tentativas.
 *
 * <p>Limitar por endereco exato nao funciona com IPv6. Uma conexao domestica
 * costuma receber um bloco {@code /64}, com 2^64 enderecos, e um provedor de
 * nuvem entrega blocos ainda maiores. Trocar de endereco dentro do proprio
 * bloco nao custa nada ao atacante, de modo que um limite por endereco exato
 * seria contornado trivialmente e, pior, cada endereco novo criaria uma entrada
 * no rastreamento.
 *
 * <p>Por isso o IPv6 e agrupado pelo prefixo {@code /48}, a alocacao tipica de
 * um site. O IPv4 permanece exato: blocos IPv4 sao escassos e agrupar por
 * {@code /24} atingiria usuarios legitimos sem relacao com o atacante.
 */
public final class ClientNetwork {

    /**
     * Bytes do prefixo IPv6 agrupado: 48 bits.
     *
     * <p>Agrupar em {@code /64} nao basta. Um {@code /48} e a alocacao tipica
     * de um site, e quem tem um {@code /48} dispoe de 65.536 blocos
     * {@code /64}: com agrupamento em {@code /64}, encher o rastreamento sairia
     * barato, e e justamente o custo de encher o rastreamento que sustenta o
     * resto do desenho deste limitador.
     *
     * <p>Agrupar mais que isso atingiria redes sem relacao entre si.
     */
    private static final int IPV6_PREFIX_BYTES = 6;

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private ClientNetwork() {
    }

    /**
     * Chave de rede para o endereco informado.
     *
     * <p>Nunca resolve nome: so interpreta literais de IP. Um valor que nao seja
     * literal e devolvido como veio, para que uma entrada desconhecida continue
     * sendo contabilizada em vez de escapar do limite.
     */
    public static String of(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }

        String address = clientIp.strip().toLowerCase(Locale.ROOT);

        // Forma IPv4 mapeada em IPv6, comum em servidores com dual stack.
        if (address.startsWith("::ffff:") && IPV4_LITERAL.matcher(address.substring(7)).matches()) {
            return address.substring(7);
        }

        if (IPV4_LITERAL.matcher(address).matches()) {
            return address;
        }

        if (address.indexOf(':') < 0) {
            // Nao e literal de IP. Nao se tenta resolver: resolucao de nome
            // numa rota publica permitiria induzir consultas DNS externas.
            return address;
        }

        return ipv6Prefix(address);
    }

    private static String ipv6Prefix(String address) {
        byte[] bytes;
        try {
            // Literal IPv6: getByName nao faz consulta de nome neste caso.
            bytes = InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException exception) {
            return address;
        }

        if (bytes.length != 16) {
            return address;
        }

        byte[] prefix = new byte[IPV6_PREFIX_BYTES];
        System.arraycopy(bytes, 0, prefix, 0, IPV6_PREFIX_BYTES);

        return HexFormat.of().formatHex(prefix) + "::/48";
    }
}
