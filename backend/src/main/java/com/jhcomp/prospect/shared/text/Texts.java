package com.jhcomp.prospect.shared.text;

/** Tratamento de texto opcional vindo de request ou de importacao. */
public final class Texts {

    private Texts() {
    }

    /** Texto sem espacos nas pontas; vazio ou so espacos vira nulo, que e "nao informado". */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
