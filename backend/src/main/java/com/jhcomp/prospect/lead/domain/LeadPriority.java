package com.jhcomp.prospect.lead.domain;

/**
 * A lead's priority band.
 *
 * <p>The score bands suggested in doc 06 §7 do not calibrate this priority
 * yet: in the MVP the operator sets it. Automatic derivation arrives with the
 * score, in stage 6.
 */
public enum LeadPriority {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}
