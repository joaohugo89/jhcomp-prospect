package com.jhcomp.prospect.analysis.infrastructure.web;

/**
 * Requisicao que nao obteve resposta HTTP.
 *
 * <p>{@code code} e um motivo curto ({@code DNS}, {@code BLOCKED_ADDRESS},
 * {@code TIMEOUT}...) que vira evidencia; a mensagem da biblioteca nao e
 * guardada, porque pode citar detalhe interno.
 */
public class FetchException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String code;

    public FetchException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
