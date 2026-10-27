package com.jhcomp.prospect.shared.web;

/** Expressoes de validacao de entrada compartilhadas pelos DTOs. */
public final class InputPatterns {

    /**
     * URL web: {@code http}, {@code https} ou sem esquema.
     *
     * <p>Recusa qualquer outro esquema ({@code javascript:}, {@code data:},
     * {@code file:}...). A URL e exibida como link na interface, e um esquema
     * arbitrario viraria execucao de script no clique. A ressalva
     * {@code :(?!\d)} deixa passar host com porta, como {@code exemplo.test:8080}.
     *
     * <p>Aceita texto vazio, que significa "sem valor".
     */
    public static final String WEB_URL = "(?i)^(?:|(?:https?://)?(?![a-z][a-z0-9+.-]*:(?!\\d))[^\\s<>\"]+)$";

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
