package com.jhcomp.prospect.identity.domain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The single password rule (D28).
 *
 * <p>Length rather than a composition rule, as NIST SP 800-63B recommends:
 * demanding a symbol and a digit produces predictable, short passwords. The
 * ceiling is in UTF-8 bytes, not characters: BCrypt only considers the first
 * 72 bytes, and Spring's encoder refuses a longer password instead of
 * truncating it.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** The reason for refusal, or empty when the password meets the policy. */
    public static Optional<String> violation(String password) {
        if (password == null || password.isBlank()) {
            return Optional.of("Password must not be blank");
        }
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return Optional.of("Password must have at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of("Password must have at most " + MAX_BYTES + " bytes");
        }
        return Optional.empty();
    }
}
