package com.jhcomp.prospect.outreach.domain;

import java.util.Optional;

import com.jhcomp.prospect.shared.normalization.ContactNormalizer;

/**
 * Normalises identifiers by the same rule as the rest of the system (D10).
 *
 * <p>Diverging from deduplication would allow a do-not-contact request to be
 * bypassed with another formatting of the same phone number.
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
