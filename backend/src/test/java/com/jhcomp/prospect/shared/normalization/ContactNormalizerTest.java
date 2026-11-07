package com.jhcomp.prospect.shared.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The shared normalisation (D10, doc 10 §4). A unit test, without Spring. */
@DisplayName("ContactNormalizer")
class ContactNormalizerTest {

    @Nested
    @DisplayName("name")
    class Name {

        @Test
        @DisplayName("removes accents, excess whitespace and case")
        void accentsAndSpaces() {
            assertThat(ContactNormalizer.name("  Salão  Beleza\t& Estética "))
                    .contains("salao beleza & estetica");
        }

        @Test
        @DisplayName("keeps punctuation: different names stay different")
        void keepsPunctuation() {
            assertThat(ContactNormalizer.name("A.B. Ferragens")).isNotEqualTo(ContactNormalizer.name("AB Ferragens"));
        }

        @Test
        @DisplayName("an empty value does not normalise")
        void blank() {
            assertThat(ContactNormalizer.name("   ")).isEmpty();
            assertThat(ContactNormalizer.name(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("phone")
    class Phone {

        @ParameterizedTest
        @ValueSource(strings = {
            "(35) 98888-7777", "35 98888 7777", "35988887777", "+55 35 98888-7777", "055 35 98888-7777", "0 35 98888.7777"
        })
        @DisplayName("different formats of the same mobile number coincide")
        void sameMobile(String raw) {
            assertThat(ContactNormalizer.phone(raw)).contains("+5535988887777");
        }

        @Test
        @DisplayName("a landline with an area code")
        void landline() {
            assertThat(ContactNormalizer.phone("(35) 3333-4444")).contains("+553533334444");
        }

        /** Assuming an area code would draw together companies from different cities. */
        @ParameterizedTest
        @ValueSource(strings = {"98888-7777", "3333-4444", "123", "+1 415 555 0100", "+55 98888 7777"})
        @DisplayName("without an area code, or outside the national format, it does not normalise")
        void unreliable(String raw) {
            assertThat(ContactNormalizer.phone(raw)).isEmpty();
        }
    }

    @Nested
    @DisplayName("domain")
    class Domain {

        @ParameterizedTest
        @ValueSource(strings = {
            "https://www.loja.example.test/contato?x=1",
            "http://LOJA.example.test",
            "loja.example.test",
            "www.loja.example.test:8080/",
            "https://loja.example.test."
        })
        @DisplayName("scheme, www, port, path and case do not matter")
        void sameDomain(String raw) {
            assertThat(ContactNormalizer.domain(raw)).contains("loja.example.test");
        }

        @ParameterizedTest
        @ValueSource(strings = {"localhost", "http://", "nao e url", "   "})
        @DisplayName("a host with no dot, or an invalid one, does not normalise")
        void invalid(String raw) {
            assertThat(ContactNormalizer.domain(raw)).isEmpty();
        }
    }

    @Nested
    @DisplayName("instagram")
    class Instagram {

        @ParameterizedTest
        @ValueSource(strings = {
                "@Loja.Exemplo", "loja.exemplo", "https://www.instagram.com/loja.exemplo/",
                "instagram.com/Loja.Exemplo", "http://instagram.com/loja.exemplo?hl=pt"})
        @DisplayName("handle, @handle and the profile URL reach the same identifier")
        void sameHandle(String raw) {
            assertThat(ContactNormalizer.instagram(raw)).contains("loja.exemplo");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://instagram.com/p/abc123", "https://instagram.com/explore/",
                "https://example.test/loja.exemplo", "https://instagram.com/", "perfil com espaco", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
        @DisplayName("refuses a post, another host and an invalid handle")
        void rejectsNonProfiles(String raw) {
            assertThat(ContactNormalizer.instagram(raw)).isEmpty();
        }
    }
}
