package com.jhcomp.prospect.outreach.domain;

/** Falha do provedor de IA: indisponivel, timeout, cota ou resposta invalida. */
public class AIProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AIProviderException(String message) {
        super(message);
    }

    public AIProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
