package com.jhcomp.prospect.analysis.infrastructure.web;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Quais enderecos o fetcher pode acessar (docs/07 §6, regra 8 do produto).
 *
 * <p>Bloqueia tudo que nao e internet publica: loopback, redes privadas,
 * link-local (inclui o endereco de metadata de nuvem 169.254.169.254), CGNAT,
 * IPv6 de uso local e faixas reservadas. Sem isso, um site cadastrado como
 * {@code http://169.254.169.254/} ou um dominio que resolve para 10.0.0.5 faria
 * o servidor consultar a propria rede interna em nome de quem cadastrou.
 *
 * <p>IPv4 embutido em IPv6 (mapeado e NAT64) e avaliado pelo IPv4 de dentro:
 * {@code ::ffff:127.0.0.1} e loopback.
 */
public final class AddressPolicy {

    /** Politica de producao: so internet publica. */
    public static final AddressPolicy PUBLIC_ONLY = new AddressPolicy(false);

    private final boolean allowLoopback;

    private AddressPolicy(boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
    }

    /** Somente para teste do fetcher contra servidor local. Nunca em bean de producao. */
    static AddressPolicy allowingLoopbackForTests() {
        return new AddressPolicy(true);
    }

    public boolean isBlocked(InetAddress address) {
        InetAddress effective = embeddedIpv4(address);
        if (effective.isLoopbackAddress()) {
            return !allowLoopback;
        }
        if (effective.isAnyLocalAddress()
                || effective.isLinkLocalAddress()
                || effective.isSiteLocalAddress()
                || effective.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = effective.getAddress();
        return effective instanceof Inet4Address ? isReservedIpv4(bytes) : isReservedIpv6(bytes);
    }

    private static boolean isReservedIpv4(byte[] b) {
        int b0 = b[0] & 0xff;
        int b1 = b[1] & 0xff;
        int b2 = b[2] & 0xff;
        return b0 == 0                                     // 0.0.0.0/8
                || (b0 == 100 && (b1 & 0xc0) == 64)        // 100.64.0.0/10, CGNAT
                || (b0 == 192 && b1 == 0 && b2 == 0)       // 192.0.0.0/24
                || (b0 == 192 && b1 == 0 && b2 == 2)       // 192.0.2.0/24, documentacao
                || (b0 == 198 && (b1 == 18 || b1 == 19))   // 198.18.0.0/15, testes
                || (b0 == 198 && b1 == 51 && b2 == 100)    // 198.51.100.0/24, documentacao
                || (b0 == 203 && b1 == 0 && b2 == 113)     // 203.0.113.0/24, documentacao
                || b0 >= 240;                              // 240.0.0.0/4 e broadcast
    }

    private static boolean isReservedIpv6(byte[] b) {
        int b0 = b[0] & 0xff;
        return (b0 & 0xfe) == 0xfc                                        // fc00::/7, uso local
                || (b0 == 0x20 && (b[1] & 0xff) == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8); // 2001:db8::/32
    }

    /** IPv4 de dentro de um IPv6 mapeado (::ffff:a.b.c.d) ou NAT64 (64:ff9b::a.b.c.d). */
    private static InetAddress embeddedIpv4(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return address;
        }
        byte[] b = address.getAddress();
        boolean mapped = allZero(b, 0, 10) && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
        boolean nat64 = (b[0] & 0xff) == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff
                && (b[3] & 0xff) == 0x9b && allZero(b, 4, 12);
        if (!mapped && !nat64) {
            return address;
        }
        try {
            return InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]});
        } catch (UnknownHostException exception) {
            // Quatro bytes sempre formam um IPv4 valido; nunca chega aqui.
            throw new IllegalStateException(exception);
        }
    }

    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
