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
 * Correlation ID por requisicao (doc 03 §11).
 *
 * <p>Fica disponivel no MDC para todo log da requisicao e e devolvido no
 * cabecalho de resposta, permitindo ligar o que o usuario viu ao que o servidor
 * registrou.
 *
 * <p>Um identificador recebido do cliente nao e reaproveitado: valor controlado
 * externamente poderia poluir ou falsificar a correlacao dos logs.
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
            // Obrigatorio: a thread volta ao pool e carregaria o valor antigo.
            MDC.remove(MDC_KEY);
        }
    }
}
