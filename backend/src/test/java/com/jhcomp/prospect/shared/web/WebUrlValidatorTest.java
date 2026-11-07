package com.jhcomp.prospect.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A URL displayed as a link (doc 05 §12).
 *
 * <p>The refused cases include the bypass a review found in the first,
 * regex-based version: {@code javascript:1,alert(1)} got through because there
 * was a digit after the colon.
 */
@DisplayName("WebUrlValidator")
class WebUrlValidatorTest {

    private final WebUrlValidator validator = new WebUrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.loja.example.test/contato?x=1#topo",
        "http://loja.example.test",
        "HTTPS://LOJA.EXAMPLE.TEST",
        "loja.example.test",
        "loja.example.test:8080/inicio",
        "www.instagram.example.test/loja_ficticia",
        "sub.dominio.example.test."
    })
    @DisplayName("accepts http, https and an address with no scheme")
    void accepts(String url) {
        assertThat(validator.isValid(url, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)",
        "JavaScript:alert(1)",
        "javascript:1,alert(document.cookie)",
        "javascript:1/alert(1)",
        "javascript://example.test/%0Aalert(1)",
        " javascript:alert(1)",
        "jav\tascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "vbscript:msgbox(1)",
        "file:///etc/passwd",
        "ftp://arquivos.example.test",
        "mailto:contato@loja.example.test",
        "https://banco.example.test@atacante.example.test",
        "localhost",
        "https://localhost:8080",
        "https://",
        "loja",
        "https://loja.example.test/\"onmouseover=alert(1)",
        "https://loja.example.test/<script>",
        "   "
    })
    @DisplayName("refuses other schemes, userinfo, a host with no dot, and injection characters")
    void rejects(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }

    @Test
    @DisplayName("empty and null mean no value")
    void emptyMeansNoValue() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
    }
}
