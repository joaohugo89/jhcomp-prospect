package com.jhcomp.prospect.shared.persistence;

/**
 * Padroes de busca por trecho para {@code LIKE}.
 *
 * <p>Escapa {@code %} e {@code _} do texto do usuario: sem isso, uma busca por
 * "%" casaria com tudo e uma por "_" com qualquer caractere, e o resultado
 * deixaria de corresponder ao que foi digitado.
 */
public final class LikePatterns {

    /** Caractere de escape a declarar na consulta. */
    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    public static String contains(String text) {
        String escaped = text
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
