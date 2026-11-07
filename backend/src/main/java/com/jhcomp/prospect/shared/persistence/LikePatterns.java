package com.jhcomp.prospect.shared.persistence;

/**
 * Substring search patterns for {@code LIKE}.
 *
 * <p>It escapes {@code %} and {@code _} in the user's text: without that, a
 * search for "%" would match everything and one for "_" any character, and the
 * result would stop corresponding to what was typed.
 */
public final class LikePatterns {

    /** The escape character to declare in the query. */
    public static final char ESCAPE = '\\';

    private LikePatterns() {
    }

    public static String contains(String text) {
        String escaped = text
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
