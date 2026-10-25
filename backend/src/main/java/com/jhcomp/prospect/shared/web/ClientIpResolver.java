package com.jhcomp.prospect.shared.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP de origem da requisicao, usado pelo limitador de login e pela auditoria.
 *
 * <p>Nao se le {@code X-Forwarded-For} diretamente: esse cabecalho e enviado
 * pelo cliente e, sem um proxy confiavel que o sobrescreva, permitiria forjar
 * o IP e escapar do limite de tentativas. Quando houver reverse proxy em
 * producao, a forma correta e configurar
 * {@code server.forward-headers-strategy=native} e deixar o container
 * resolver, mantendo este codigo inalterado.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
