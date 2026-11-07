package com.jhcomp.prospect.outreach.domain;

/**
 * The message's state (doc 04, D26).
 *
 * <p>Approved is not sent, and {@code SENT} only exists once the operator
 * records it (product rule 5).
 */
public enum MessageStatus {
    DRAFT,
    /** The AI flagged unverified claims: approving requires explicit confirmation. */
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
