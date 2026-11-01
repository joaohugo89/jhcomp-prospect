package com.jhcomp.prospect.outreach.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jhcomp.prospect.shared.normalization.ContactNormalizer;

/** D10: a lista de nao contato normaliza como a deduplicacao. Dados ficticios. */
@DisplayName("ContactIdentifiers")
class ContactIdentifiersTest {

    @Test
    @DisplayName("telefone em outra formatacao vira o mesmo identificador da deduplicacao")
    void phoneMatchesDedup() {
        assertThat(ContactIdentifiers.normalize(IdentifierType.PHONE, "(35) 98888-7777"))
                .map(ContactIdentifier::value)
                .isEqualTo(ContactNormalizer.phone("+55 35 988887777"));
    }

    @Test
    @DisplayName("e-mail em minusculas e sem espacos")
    void email() {
        assertThat(ContactIdentifiers.normalize(IdentifierType.EMAIL, " Contato@Loja.Example.Test "))
                .contains(new ContactIdentifier(IdentifierType.EMAIL, "contato@loja.example.test"));
    }

    @Test
    @DisplayName("valor que nao normaliza nao vira identificador")
    void invalid() {
        assertThat(ContactIdentifiers.normalize(IdentifierType.PHONE, "98888-7777")).isEmpty();
    }
}
