package com.jhcomp.prospect.analysis.domain;

/** Revisao humana do achado (RF-033). */
public enum ReviewStatus {
    PENDING,
    /** O operador conferiu e manteve o estado. */
    CONFIRMED,
    /** O operador trocou o estado. O original continua registrado. */
    CORRECTED
}
