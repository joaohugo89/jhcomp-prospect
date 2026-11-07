package com.jhcomp.prospect.shared.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The origin networks of an address, at two levels, for rate limiting (D20).
 *
 * <p>Limiting by exact address does not work with IPv6: a subscriber receives
 * a whole {@code /64} block and can change address inside it at no cost. IPv6
 * is therefore grouped at two levels:
 *
 * <ul>
 *   <li>{@code subscriber}: the {@code /64}, which corresponds to one
 *       subscriber. It carries the normal quota, so an abusive subscriber does
 *       not hit the neighbours.
 *   <li>{@code site}: the {@code /48}, a typical site allocation. Whoever
 *       holds a {@code /48} has 65,536 {@code /64} blocks available; the
 *       aggregate ceiling stops them multiplying quotas and tracking entries
 *       by rotating blocks.
 * </ul>
 *
 * <p>IPv4 is exact at both levels: IPv4 blocks are scarce, and grouping by
 * {@code /24} would hit legitimate users with no relation to the attacker.
 *
 * @param subscriber the subscriber key: an exact IPv4 or a {@code /64} prefix
 * @param site the site key: an exact IPv4 or a {@code /48} prefix
 */
public record ClientNetwork(String subscriber, String site) {

    private static final int SUBSCRIBER_PREFIX_BYTES = 8;
    private static final int SITE_PREFIX_BYTES = 6;

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /**
     * The networks of the address given.
     *
     * <p>It never resolves a name: it only interprets IP literals. A value
     * that is not a literal is used as it came at both levels, so an unknown
     * entry is still counted rather than escaping the limit.
     */
    public static ClientNetwork of(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return exact("unknown");
        }

        String address = clientIp.strip().toLowerCase(Locale.ROOT);

        // The IPv4-mapped IPv6 form, common on dual-stack servers.
        if (address.startsWith("::ffff:") && IPV4_LITERAL.matcher(address.substring(7)).matches()) {
            return exact(address.substring(7));
        }

        if (IPV4_LITERAL.matcher(address).matches() || address.indexOf(':') < 0) {
            // IPv4, or a value that is not an IP literal. No resolution is
            // attempted: resolving a name on a public route would allow
            // external DNS lookups to be induced.
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
            // An IPv6 literal: getByName performs no name lookup in this case.
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
