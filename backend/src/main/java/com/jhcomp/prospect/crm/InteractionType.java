package com.jhcomp.prospect.crm;

/** Tipo de interacao (RF-041). */
public enum InteractionType {
    CONTACT,
    REPLY,
    MEETING,
    PROPOSAL,
    /** Pedido de nao contato: bloqueia o lead na hora (doc 06 §9). */
    DO_NOT_CONTACT_REQUEST,
    NOTE;

    /** Tipos que sao abordagem a empresa: proibidos para lead bloqueado (regra 4). */
    boolean isApproach() {
        return this == CONTACT || this == MEETING || this == PROPOSAL;
    }
}
