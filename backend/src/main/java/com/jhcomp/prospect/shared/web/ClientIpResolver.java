package com.jhcomp.prospect.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The request's origin IP, used by the login limiter and by the audit trail.
 *
 * <p>{@code X-Forwarded-For} is not read directly: that header is sent by the
 * client and, without a trusted proxy overwriting it, would allow the IP to be
 * forged and the attempt limit to be escaped. With a reverse proxy in
 * production the correct approach is to set
 * {@code server.forward-headers-strategy=native} and let the container
 * resolve it, leaving this code unchanged.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
