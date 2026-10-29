package com.jhcomp.prospect.analysis.domain;

/**
 * Estado de verificacao de um achado (doc 06 §5, docs/04 §2).
 *
 * <p>Cada criterio e uma afirmacao sobre a empresa. O estado diz o que se sabe
 * sobre ela. So {@link #CONFIRMED} pontua: "nao encontrado" significa apenas
 * que a pesquisa nao achou, nunca prova de inexistencia (regra 3 do produto).
 */
public enum FindingStatus {
    /** A afirmacao foi verificada e vale. */
    CONFIRMED,
    /** A pesquisa nao encontrou a informacao. Nao e prova de que ela falta. */
    NOT_FOUND,
    /** Nao foi possivel verificar: site fora do ar, robots.txt, criterio manual. */
    NOT_VERIFIED,
    /** A afirmacao nao se aplica a esta empresa. */
    NOT_APPLICABLE,
    /** Ha evidencia de que a afirmacao e falsa. */
    CONTRADICTED
}
