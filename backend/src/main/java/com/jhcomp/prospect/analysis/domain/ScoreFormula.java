package com.jhcomp.prospect.analysis.domain;

import java.util.Map;

/**
 * Score deterministico (RF-032, doc 06 §7).
 *
 * <p>Funcao pura dos estados dos achados: os mesmos estados dao sempre o mesmo
 * score, e a versao gravada junto permite reproduzir o calculo depois que a
 * formula mudar (D3).
 */
public final class ScoreFormula {

    public static final String VERSION = "v1";

    private ScoreFormula() {
    }

    /** Pontos de um criterio: todos se confirmado, nenhum caso contrario. */
    public static int pointsFor(Criterion criterion, FindingStatus status) {
        return status == FindingStatus.CONFIRMED ? criterion.maxPoints() : 0;
    }

    public static int score(Map<Criterion, FindingStatus> statuses) {
        return statuses.entrySet().stream()
                .mapToInt(entry -> pointsFor(entry.getKey(), entry.getValue()))
                .sum();
    }
}
