package com.jhcomp.prospect.analysis.domain;

/**
 * Criterios da formula de score v1 (doc 06 §7, D24).
 *
 * <p>Cada criterio e uma afirmacao e vale seus pontos inteiros quando
 * confirmada, zero caso contrario. Os grupos somam exatamente a tabela do
 * doc 06 §7: ausencia de site 25, problema tecnico 20, necessidade funcional
 * 20, informacoes comerciais 15, servicos 10, localizacao 10.
 */
public enum Criterion {
    /** Ausencia de site confirmada por metodo adequado: so por revisao humana. */
    SITE_ABSENT(25),
    NO_HTTPS(10),
    NO_MOBILE_VIEWPORT(10),
    NO_CONTACT_LINK(10),
    /** Sem heuristica automatica: inferir pelo texto seria afirmar sem evidencia. */
    NO_ONLINE_BOOKING_OR_CATALOG(10),
    BUSINESS_INFO_SUFFICIENT(15),
    SERVICE_FIT(10),
    LOCATION_SERVED(10);

    private final int maxPoints;

    Criterion(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public int maxPoints() {
        return maxPoints;
    }
}
