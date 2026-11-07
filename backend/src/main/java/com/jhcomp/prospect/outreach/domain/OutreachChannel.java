package com.jhcomp.prospect.outreach.domain;

/**
 * An outreach channel (D26).
 *
 * <p>Each channel has its own size limit and the identifier type that points
 * at the recipient: that identifier is what the do-not-contact list is
 * checked against.
 */
public enum OutreachChannel {
    WHATSAPP(1_000, IdentifierType.PHONE),
    EMAIL(4_000, IdentifierType.EMAIL),
    INSTAGRAM(1_000, IdentifierType.INSTAGRAM),
    /** A call script, not a written message. */
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
