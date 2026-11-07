package com.jhcomp.prospect.identity.domain;

/**
 * A member's role within the organisation (RF-003, doc 01 §6).
 *
 * <p>Least privilege needed: {@link #OPERATOR} runs the commercial process,
 * {@link #ADMIN} administers the organisation.
 */
public enum OrganizationRole {
    ADMIN,
    OPERATOR;

    /** The authority name Spring Security uses, which expects the ROLE_ prefix. */
    public String authority() {
        return "ROLE_" + name();
    }
}
