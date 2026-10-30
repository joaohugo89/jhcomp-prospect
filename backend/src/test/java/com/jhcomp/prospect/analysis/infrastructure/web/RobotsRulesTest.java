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

    /**
     * Padrao que leva tempo exponencial numa regex com backtracking. O
     * comparador sem regex precisa responder na hora.
     */
    @Test
    @DisplayName("regra patologica nao trava a decisao (ReDoS)")
    void pathologicalPatternIsFast() {
        String rule = "/" + "*a".repeat(40) + "b";
        RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: " + rule + "\n");

        long started = System.nanoTime();
        boolean allowed = rules.allows("/" + "a".repeat(2000));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(allowed).isTrue();
        assertThat(elapsedMillis).isLessThan(500);
    }

    @Test
    @DisplayName("curinga casa como no padrao do Google")
    void wildcardSemantics() {
        assertThat(RobotsRules.matches("/fish", "/fish.html")).isTrue();
        assertThat(RobotsRules.matches("/fish$", "/fish.html")).isFalse();
        assertThat(RobotsRules.matches("/*.php", "/index.php?x=1")).isTrue();
        assertThat(RobotsRules.matches("/*.php$", "/index.php?x=1")).isFalse();
        assertThat(RobotsRules.matches("/a*b*c", "/aXbYc")).isTrue();
        assertThat(RobotsRules.matches("/a*b*c", "/acb")).isFalse();
    }

    @Test
    @DisplayName("limites de conteudo externo: regras demais ou caminho enorme")
    void contentLimits() {
        StringBuilder robots = new StringBuilder("User-agent: *\n");
        for (int i = 0; i < RobotsRules.MAX_RULES; i++) {
            robots.append("Disallow: /nada-").append(i).append('\n');
        }
        // Alem do teto: ignorada.
        robots.append("Disallow: /\n");

        assertThat(RobotsRules.parse(robots.toString()).allows("/")).isTrue();
        // Caminho alem do limite: sem decisao segura, nao le.
        assertThat(RobotsRules.allowAll().allows("/" + "a".repeat(RobotsRules.MAX_PATH_LENGTH))).isFalse();
    }
}
