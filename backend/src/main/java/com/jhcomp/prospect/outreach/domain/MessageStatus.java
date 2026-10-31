package com.jhcomp.prospect.outreach.domain;

/**
 * Estado da mensagem (doc 04, D26).
 *
 * <p>Aprovada nao e enviada, e {@code SENT} so existe com registro do
 * operador (regra 5 do produto).
 */
public enum MessageStatus {
    DRAFT,
    /** A IA marcou afirmacoes nao verificadas: aprovar exige confirmacao explicita. */
    NEEDS_REVIEW,
    APPROVED,
    SENT,
    FAILED,
    CANCELLED;

    public boolean isFinal() {
        return this == SENT || this == FAILED || this == CANCELLED;
    }

    public boolean isAwaitingApproval() {
        return this == DRAFT || this == NEEDS_REVIEW;
    }
}
