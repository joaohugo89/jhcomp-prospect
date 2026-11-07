package com.jhcomp.prospect.analysis.domain;

/**
 * The verification state of a finding (doc 06 §5, docs/04 §2).
 *
 * <p>Each criterion is a statement about the company. The state says what is
 * known about it. Only {@link #CONFIRMED} scores: "not found" means merely
 * that the search did not find it, never proof of absence (product rule 3).
 */
public enum FindingStatus {
    /** The statement was verified and holds. */
    CONFIRMED,
    /** The search did not find the information. That is not proof it is missing. */
    NOT_FOUND,
    /** Verification was not possible: site down, robots.txt, or a manual criterion. */
    NOT_VERIFIED,
    /** The statement does not apply to this company. */
    NOT_APPLICABLE,
    /** There is evidence that the statement is false. */
    CONTRADICTED
}
