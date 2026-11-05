package com.jhcomp.prospect.shared.persistence;

import java.sql.Timestamp;
import java.time.Instant;

/** Conversao de valores lidos em consultas SQL e agregacoes JPQL, num lugar so. */
public final class SqlValues {

    private SqlValues() {
    }

    /** {@code timestamptz} lido pelo JDBC; nulo continua nulo. */
    public static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** Contagem ou soma de uma agregacao: o tipo varia com o driver (Long, BigDecimal); nulo e zero. */
    public static long count(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}
