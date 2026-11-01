package com.jhcomp.prospect.identity.domain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Regra unica de senha (D28).
 *
 * <p>Comprimento em vez de regra de composicao, como recomenda o NIST SP
 * 800-63B: exigir simbolo e numero produz senhas previsiveis e curtas. O teto
 * e em bytes UTF-8, nao em caracteres: o BCrypt so considera os primeiros 72
 * bytes, e o codificador do Spring recusa senha maior em vez de truncar.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** Motivo da recusa, ou vazio quando a senha atende a politica. */
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
