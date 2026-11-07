package com.jhcomp.prospect.outreach.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jhcomp.prospect.shared.normalization.ContactNormalizer;

/** D10: the do-not-contact list normalises the way deduplication does. Fictitious data. */
@DisplayName("ContactIdentifiers")
class ContactIdentifiersTest {

    @Test
    @DisplayName("a phone in another format becomes the same identifier deduplication uses")
    void phoneMatchesDedup() {
        assertThat(ContactIdentifiers.normalize(IdentifierType.PHONE, "(35) 98888-7777"))
                .map(ContactIdentifier::value)
                .isEqualTo(ContactNormalizer.phone("+55 35 988887777"));
    }

    @Test
    @DisplayName("an e-mail in lower case and without spaces")
    void email() {
        assertThat(ContactIdentifiers.normalize(IdentifierType.EMAIL, " Contato@Loja.Example.Test "))
                .contains(new ContactIdentifier(IdentifierType.EMAIL, "contato@loja.example.test"));
    }

    @Test
    @DisplayName("a value that does not normalise does not become an identifier")
    void invalid() {
        assertThat(ContactIdentifiers.normalize(IdentifierType.PHONE, "98888-7777")).isEmpty();
    }
}
