package com.jhcomp.prospect.shared.web;

/** Input validation expressions shared by the DTOs. */
public final class InputPatterns {

    /**
     * A phone as typed: digits, spaces, parentheses, hyphens, dots and +. It
     * accepts an empty string.
     */
    public static final String PHONE = "^(?:|[0-9+().\\-\\s]{8,25})$";

    /** A two-letter state code. */
    public static final String STATE = "^[A-Za-z]{2}$";

    /** At least one non-whitespace character. */
    public static final String NOT_BLANK_WHEN_PRESENT = "(?s).*\\S.*";

    private InputPatterns() {
    }
}
