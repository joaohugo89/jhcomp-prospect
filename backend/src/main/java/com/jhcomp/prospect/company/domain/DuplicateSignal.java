package com.jhcomp.prospect.company.domain;

/**
 * Sinal que aproximou duas empresas (doc 06 §4), do mais forte ao mais fraco.
 *
 * <p>Sinal e sugestao, nunca decisao: nenhuma mesclagem acontece sem revisao
 * humana (RF-014).
 */
public enum DuplicateSignal {
    DOMAIN,
    PHONE,
    /** Nome normalizado igual na mesma cidade e estado. */
    NAME_CITY_STATE
}
