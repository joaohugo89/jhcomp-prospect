package com.jhcomp.prospect.analysis.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** robots.txt (RFC 9309, doc 06 §6). Unitario. */
@DisplayName("RobotsRules")
class RobotsRulesTest {

    @Test
    @DisplayName("Disallow / no grupo * proibe a pagina inicial")
    void disallowAll() {
        RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /\n");

        assertThat(rules.allows("/")).isFalse();
        assertThat(rules.allows("/contato")).isFalse();
    }

    @Test
    @DisplayName("Disallow vazio e arquivo sem regras nao proibem nada")
    void emptyDisallow() {
        assertThat(RobotsRules.parse("User-agent: *\nDisallow:\n").allows("/")).isTrue();
        assertThat(RobotsRules.parse("# so comentario\n").allows("/")).isTrue();
    }

    @Test
    @DisplayName("grupo do nosso agente prevalece sobre o grupo *")
    void specificGroupWins() {
        String robots = """
                User-agent: *
                Disallow: /

                User-agent: JHCOMP-Prospect
                Allow: /
                """;

        assertThat(RobotsRules.parse(robots).allows("/")).isTrue();
    }

    @Test
    @DisplayName("regra mais longa vence; empate favorece Allow")
    void longestMatch() {
        RobotsRules rules = RobotsRules.parse("""
                User-agent: *
                Disallow: /admin
                Allow: /admin/publico
                Disallow: /x
                Allow: /x
                """);

        assertThat(rules.allows("/admin/painel")).isFalse();
        assertThat(rules.allows("/admin/publico/pagina")).isTrue();
        assertThat(rules.allows("/x")).isTrue();
        assertThat(rules.allows("/")).isTrue();
    }

    @Test
    @DisplayName("curinga e ancora de fim")
    void wildcardAndAnchor() {
        RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /*.pdf$\n");

        assertThat(rules.allows("/arquivo.pdf")).isFalse();
        assertThat(rules.allows("/arquivo.pdf.html")).isTrue();
    }

    @Test
    @DisplayName("agentes de outros robos nao afetam a decisao")
    void otherAgentsIgnored() {
        assertThat(RobotsRules.parse("User-agent: Googlebot\nDisallow: /\n").allows("/")).isTrue();
    }

    @Test
    @DisplayName("erro do servidor no robots.txt proibe tudo; ausencia libera")
    void statusDefaults() {
        assertThat(RobotsRules.disallowAll().allows("/")).isFalse();
        assertThat(RobotsRules.allowAll().allows("/")).isTrue();
    }
}
