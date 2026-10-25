package com.jhcomp.prospect.lead.domain;

/**
 * Faixa de prioridade do lead.
 *
 * <p>As faixas de score sugeridas no doc 06 §7 ainda nao calibram esta
 * prioridade: no MVP ela e definida pelo operador. A derivacao automatica
 * entra com o score, na Etapa 6.
 */
public enum LeadPriority {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}
