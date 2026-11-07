package com.jhcomp.prospect.shared.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A correlation id per request (doc 03 §11).
 *
 * <p>It is available in the MDC for every log line of the request and is
 * returned in a response header, which is what links what the user saw to what
 * the server recorded.
 *
 * <p>An identifier received from the client is not reused: an externally
 * controlled value could pollute or forge the correlation of the logs.
 */
@Component
@Order(Integer.MIN_VALUE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Mandatory: the thread returns to the pool and would carry the old value.
            MDC.remove(MDC_KEY);
        }
    }
}
