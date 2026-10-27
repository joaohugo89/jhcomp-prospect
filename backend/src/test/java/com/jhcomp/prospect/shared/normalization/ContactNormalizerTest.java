package com.jhcomp.prospect.shared.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Normalizacao compartilhada (D10, doc 10 §4). Unitario, sem Spring. */
@DisplayName("ContactNormalizer")
class ContactNormalizerTest {

    @Nested
    @DisplayName("nome")
    class Name {

        @Test
        @DisplayName("remove acento, espaco excedente e caixa")
        void accentsAndSpaces() {
            assertThat(ContactNormalizer.name("  Salão  Beleza\t& Estética "))
                    .contains("salao beleza & estetica");
        }

        @Test
        @DisplayName("mantem pontuacao: nomes diferentes continuam diferentes")
        void keepsPunctuation() {
            assertThat(ContactNormalizer.name("A.B. Ferragens")).isNotEqualTo(ContactNormalizer.name("AB Ferragens"));
        }

        @Test
        @DisplayName("vazio nao normaliza")
        void blank() {
            assertThat(ContactNormalizer.name("   ")).isEmpty();
            assertThat(ContactNormalizer.name(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("telefone")
    class Phone {

        @ParameterizedTest
        @ValueSource(strings = {
            "(35) 98888-7777", "35 98888 7777", "35988887777", "+55 35 98888-7777", "055 35 98888-7777", "0 35 98888.7777"
        })
        @DisplayName("formatacoes diferentes do mesmo celular coincidem")
        void sameMobile(String raw) {
            assertThat(ContactNormalizer.phone(raw)).contains("+5535988887777");
        }

        @Test
        @DisplayName("fixo com DDD")
        void landline() {
            assertThat(ContactNormalizer.phone("(35) 3333-4444")).contains("+553533334444");
        }

        /** Assumir DDD aproximaria empresas de cidades diferentes. */
        @ParameterizedTest
        @ValueSource(strings = {"98888-7777", "3333-4444", "123", "+1 415 555 0100", "+55 98888 7777"})
        @DisplayName("sem DDD ou fora do padrao nacional nao normaliza")
        void unreliable(String raw) {
            assertThat(ContactNormalizer.phone(raw)).isEmpty();
        }
    }

    @Nested
    @DisplayName("dominio")
    class Domain {

        @ParameterizedTest
        @ValueSource(strings = {
            "https://www.loja.example.test/contato?x=1",
            "http://LOJA.example.test",
            "loja.example.test",
            "www.loja.example.test:8080/",
            "https://loja.example.test."
        })
        @DisplayName("esquema, www, porta, caminho e caixa nao importam")
        void sameDomain(String raw) {
            assertThat(ContactNormalizer.domain(raw)).contains("loja.example.test");
        }

        @ParameterizedTest
        @ValueSource(strings = {"localhost", "http://", "nao e url", "   "})
        @DisplayName("host sem ponto ou invalido nao normaliza")
        void invalid(String raw) {
            assertThat(ContactNormalizer.domain(raw)).isEmpty();
        }
    }

    @Nested
    @DisplayName("identificador de nao contato")
    class ContactIdentifier {

        /** D10: a lista de nao contato usa a mesma normalizacao da deduplicacao. */
        @Test
        @DisplayName("telefone usa a mesma regra da deduplicacao")
        void phoneMatchesDedup() {
            assertThat(ContactNormalizer.contactIdentifier("(35) 98888-7777"))
                    .isEqualTo(ContactNormalizer.phone("+55 35 988887777"));
        }

        @Test
        @DisplayName("e-mail em minusculas")
        void email() {
            assertThat(ContactNormalizer.contactIdentifier(" Contato@Loja.Example.Test "))
                    .contains("contato@loja.example.test");
        }
    }
}
