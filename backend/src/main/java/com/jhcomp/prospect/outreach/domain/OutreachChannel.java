package com.jhcomp.prospect.outreach.domain;

/**
 * Canal de abordagem (D26).
 *
 * <p>Cada canal tem limite proprio de tamanho e o tipo de identificador que
 * aponta o destinatario: e por ele que a lista de nao contato e conferida.
 */
public enum OutreachChannel {
    WHATSAPP(1_000, IdentifierType.PHONE),
    EMAIL(4_000, IdentifierType.EMAIL),
    INSTAGRAM(1_000, IdentifierType.INSTAGRAM),
    /** Roteiro de ligacao, nao mensagem escrita. */
    PHONE_CALL(2_000, IdentifierType.PHONE);

    private final int maxBodyLength;
    private final IdentifierType recipientType;

    OutreachChannel(int maxBodyLength, IdentifierType recipientType) {
        this.maxBodyLength = maxBodyLength;
        this.recipientType = recipientType;
    }

    public int maxBodyLength() {
        return maxBodyLength;
    }

    public IdentifierType recipientType() {
        return recipientType;
    }

    public boolean allowsSubject() {
        return this == EMAIL;
    }
}
