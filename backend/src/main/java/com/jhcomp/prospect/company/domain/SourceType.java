package com.jhcomp.prospect.company.domain;

/** Tipo de origem de um dado (RF-020, RF-022). */
public enum SourceType {
    /** Registrado pelo operador a partir de pesquisa propria. */
    MANUAL,
    CSV_IMPORT,
    /** Fonte externa por adaptador (RF-021, P1). */
    INTEGRATION
}
