package com.jhcomp.prospect.shared.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Forces the CSRF token cookie to be issued.
 *
 * <p>Spring Security loads the token lazily: the cookie is only written once
 * something actually reads the value. In an API consumed by a single-page
 * application that creates a deadlock — the client needs the token to make the
 * first POST, but no earlier GET issued the cookie. Reading the value here
 * breaks the deadlock.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            // The call itself is what triggers the cookie being written.
            token.getToken();
        }

        filterChain.doFilter(request, response);
    }
}
