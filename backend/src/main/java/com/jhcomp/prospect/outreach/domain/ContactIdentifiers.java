package com.jhcomp.prospect.outreach.domain;

import java.util.Optional;

import com.jhcomp.prospect.shared.normalization.ContactNormalizer;

/**
 * Normaliza identificadores pela mesma regra do resto do sistema (D10).
 *
 * <p>Divergir da deduplicacao permitiria burlar um pedido de nao contato com
 * outra formatacao do mesmo telefone.
 */
public final class ContactIdentifiers {

    private ContactIdentifiers() {
    }

    public static Optional<ContactIdentifier> normalize(IdentifierType type, String rawValue) {
        Optional<String> value = switch (type) {
            case PHONE -> ContactNormalizer.phone(rawValue);
            case EMAIL -> ContactNormalizer.email(rawValue);
            case INSTAGRAM -> ContactNormalizer.instagram(rawValue);
        };
        return value.map(normalized -> new ContactIdentifier(type, normalized));
    }
}
