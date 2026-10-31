package com.jhcomp.prospect.outreach.domain;

import java.util.Objects;

/** Identificador de contato ja normalizado: so serve para comparar, nunca para exibir. */
public record ContactIdentifier(IdentifierType type, String value) {

    public ContactIdentifier {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
    }
}
