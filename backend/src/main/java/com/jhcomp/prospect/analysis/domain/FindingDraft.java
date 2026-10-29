package com.jhcomp.prospect.analysis.domain;

import java.time.Instant;
import java.util.List;

/** Achado produzido pela verificacao, antes de ser gravado. */
public record FindingDraft(
        Criterion criterion, FindingStatus status, String description, Confidence confidence, List<Evidence> evidence) {

    /** Evidencia curta: referencia e descricao, nunca copia da pagina (docs/04 §2). */
    public record Evidence(
            EvidenceType type, String referenceUrl, String description, Instant observedAt, String capturedBy,
            String contentHash) {}

    public int points() {
        return ScoreFormula.pointsFor(criterion, status);
    }
}
