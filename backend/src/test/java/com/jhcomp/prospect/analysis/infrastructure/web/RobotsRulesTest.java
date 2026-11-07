package com.jhcomp.prospect.analysis.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** robots.txt (RFC 9309, doc 06 §6). A unit test. */
@DisplayName("RobotsRules")
class RobotsRulesTest {

    @Test
    @DisplayName("Disallow / in the * group forbids the home page")
    void disallowAll() {
        RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /\n");

        assertThat(rules.allows("/")).isFalse();
        assertThat(rules.allows("/contato")).isFalse();
    }

    @Test
    @DisplayName("an empty Disallow and a file with no rules forbid nothing")
    void emptyDisallow() {
        assertThat(RobotsRules.parse("User-agent: *\nDisallow:\n").allows("/")).isTrue();
        assertThat(RobotsRules.parse("# so comentario\n").allows("/")).isTrue();
    }

    @Test
    @DisplayName("our agent's group prevails over the * group")
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
    @DisplayName("the longest rule wins; a tie favours Allow")
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
    @DisplayName("wildcard and end anchor")
    void wildcardAndAnchor() {
        RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /*.pdf$\n");

        assertThat(rules.allows("/arquivo.pdf")).isFalse();
        assertThat(rules.allows("/arquivo.pdf.html")).isTrue();
    }

    @Test
    @DisplayName("other robots' agents do not affect the decision")
    void otherAgentsIgnored() {
        assertThat(RobotsRules.parse("User-agent: Googlebot\nDisallow: /\n").allows("/")).isTrue();
    }

    @Test
    @DisplayName("a server error on robots.txt forbids everything; its absence allows")
    void statusDefaults() {
        assertThat(RobotsRules.disallowAll().allows("/")).isFalse();
        assertThat(RobotsRules.allowAll().allows("/")).isTrue();
    }

    /**
     * A pattern that takes exponential time in a backtracking regex. The
     * regex-free matcher has to answer immediately.
     */
    @Test
    @DisplayName("a pathological rule does not stall the decision (ReDoS)")
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
    @DisplayName("wildcards match as in Google's parser")
    void wildcardSemantics() {
        assertThat(RobotsRules.matches("/fish", "/fish.html")).isTrue();
        assertThat(RobotsRules.matches("/fish$", "/fish.html")).isFalse();
        assertThat(RobotsRules.matches("/*.php", "/index.php?x=1")).isTrue();
        assertThat(RobotsRules.matches("/*.php$", "/index.php?x=1")).isFalse();
        assertThat(RobotsRules.matches("/a*b*c", "/aXbYc")).isTrue();
        assertThat(RobotsRules.matches("/a*b*c", "/acb")).isFalse();
    }

    @Test
    @DisplayName("limits on external content: too many rules, or an enormous path")
    void contentLimits() {
        StringBuilder robots = new StringBuilder("User-agent: *\n");
        for (int i = 0; i < RobotsRules.MAX_RULES; i++) {
            robots.append("Disallow: /nada-").append(i).append('\n');
        }
        // Beyond the cap: ignored.
        robots.append("Disallow: /\n");

        assertThat(RobotsRules.parse(robots.toString()).allows("/")).isTrue();
        // A path beyond the limit: with no safe decision, it does not read.
        assertThat(RobotsRules.allowAll().allows("/" + "a".repeat(RobotsRules.MAX_PATH_LENGTH))).isFalse();
    }
}
