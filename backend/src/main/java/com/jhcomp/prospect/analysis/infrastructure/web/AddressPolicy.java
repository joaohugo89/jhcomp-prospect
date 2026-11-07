package com.jhcomp.prospect.analysis.infrastructure.web;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Which addresses the fetcher may reach (docs/07 §6, product rule 8).
 *
 * <p>It blocks everything that is not the public internet: loopback, private
 * networks, link-local (including the cloud metadata address
 * 169.254.169.254), CGNAT, IPv6 local use and reserved ranges. Without this, a
 * site registered as {@code http://169.254.169.254/}, or a domain resolving to
 * 10.0.0.5, would make the server query its own internal network on behalf of
 * whoever registered it.
 *
 * <p>An IPv4-mapped IPv6 address is judged by the IPv4 inside it:
 * {@code ::ffff:127.0.0.1} is loopback. For IPv6 an allowlist applies: global
 * unicast only.
 */
public final class AddressPolicy {

    /** The production policy: public internet only. */
    public static final AddressPolicy PUBLIC_ONLY = new AddressPolicy(false);

    private final boolean allowLoopback;

    private AddressPolicy(boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
    }

    /** For testing the fetcher against a local server only. Never in a production bean. */
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

    /**
     * IPv6 by allowlist: global unicast only (2000::/3), minus the ranges that
     * carry or forward IPv4 and the documentation ones. A blocklist would let
     * a forgotten range through; here the unknown is refused.
     */
    private static boolean isReservedIpv6(byte[] b) {
        int b0 = b[0] & 0xff;
        int b1 = b[1] & 0xff;
        int b2 = b[2] & 0xff;
        int b3 = b[3] & 0xff;
        boolean globalUnicast = (b0 & 0xe0) == 0x20;
        return !globalUnicast
                || (b0 == 0x20 && b1 == 0x02)                                     // 2002::/16, 6to4
                || (b0 == 0x20 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00)        // 2001::/32, Teredo
                || (b0 == 0x20 && b1 == 0x01 && b2 == 0x00 && (b3 & 0xf0) == 0x10) // 2001:10::/28, ORCHID
                || (b0 == 0x20 && b1 == 0x01 && b2 == 0x0d && b3 == 0xb8);       // 2001:db8::/32, documentacao
    }

    /**
     * The IPv4 inside a mapped IPv6 address (::ffff:a.b.c.d), judged as IPv4.
     * The other forms of embedded IPv4 (NAT64, 6to4, Teredo) fall outside
     * 2000::/3 or are excluded explicitly, and are refused.
     */
    private static InetAddress embeddedIpv4(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return address;
        }
        byte[] b = address.getAddress();
        boolean mapped = allZero(b, 0, 10) && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
        if (!mapped) {
            return address;
        }
        try {
            return InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]});
        } catch (UnknownHostException exception) {
            // Four bytes always form a valid IPv4; this is never reached.
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
