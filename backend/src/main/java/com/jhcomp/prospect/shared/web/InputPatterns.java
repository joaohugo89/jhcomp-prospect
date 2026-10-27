package com.jhcomp.prospect.shared.web;

/** Expressoes de validacao de entrada compartilhadas pelos DTOs. */
public final class InputPatterns {

    /**
     * Telefone como digitado: digitos, espaco, parenteses, hifen, ponto e +.
     * Aceita texto vazio.
     */
    public static final String PHONE = "^(?:|[0-9+().\\-\\s]{8,25})$";

    /** Sigla de estado com duas letras. */
    public static final String STATE = "^[A-Za-z]{2}$";

    /** Ao menos um caractere que nao seja espaco. */
    public static final String NOT_BLANK_WHEN_PRESENT = "(?s).*\\S.*";

    private InputPatterns() {
    }
}
