package com.jhcomp.prospect.shared.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Forca a emissao do cookie com o token CSRF.
 *
 * <p>O Spring Security carrega o token de forma diferida: o cookie so e escrito
 * quando alguem efetivamente le o valor. Numa API consumida por SPA isso cria
 * um impasse — o cliente precisa do token para fazer o primeiro POST, mas
 * nenhum GET anterior emitiu o cookie. Ler o valor aqui resolve o impasse.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            // A chamada em si e o que dispara a escrita do cookie.
            token.getToken();
        }

        filterChain.doFilter(request, response);
    }
}
