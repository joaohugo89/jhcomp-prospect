package com.jhcomp.prospect.company.domain;

/**
 * The signal that brought two companies together (doc 06 §4), strongest
 * first.
 *
 * <p>A signal is a suggestion, never a decision: no merge happens without
 * human review (RF-014).
 */
public enum DuplicateSignal {
    DOMAIN,
    PHONE,
    /** An identical normalised name in the same city and state. */
    NAME_CITY_STATE
}
