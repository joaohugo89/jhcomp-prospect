package com.jhcomp.prospect.lead.domain;

/** Ciclo de vida do lead (doc 06 §2). */
public enum LeadStatus {
    NEW,
    QUALIFIED,
    CONTACT_APPROVED,
    CONTACTED,
    IN_CONVERSATION,
    PROPOSAL_SENT,
    WON,
    LOST,
    DO_NOT_CONTACT;

    /** {@code WON} e {@code LOST} encerram a oportunidade (doc 06 §2). */
    public boolean isTerminal() {
        return this == WON || this == LOST;
    }
}
