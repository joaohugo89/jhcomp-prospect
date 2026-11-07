package com.jhcomp.prospect.analysis.domain;

import java.util.Map;

/**
 * The deterministic score (RF-032, doc 06 §7).
 *
 * <p>A pure function of the findings' states: the same states always give the
 * same score, and the version stored alongside allows the calculation to be
 * reproduced after the formula changes (D3).
 */
public final class ScoreFormula {

    public static final String VERSION = "v1";

    private ScoreFormula() {
    }

    /** A criterion's points: all of them when confirmed, none otherwise. */
    public static int pointsFor(Criterion criterion, FindingStatus status) {
        return status == FindingStatus.CONFIRMED ? criterion.maxPoints() : 0;
    }

    public static int score(Map<Criterion, FindingStatus> statuses) {
        return statuses.entrySet().stream()
                .mapToInt(entry -> pointsFor(entry.getKey(), entry.getValue()))
                .sum();
    }
}
