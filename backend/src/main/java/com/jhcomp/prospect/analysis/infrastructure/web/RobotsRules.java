package com.jhcomp.prospect.analysis.infrastructure.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Regras de um robots.txt (RFC 9309), so o necessario para decidir se um
 * caminho pode ser lido (doc 06 §6).
 *
 * <p>Usa o grupo do nosso agente, se houver, senao o grupo {@code *}. Entre
 * as regras que casam, vence a mais longa; empate favorece {@code Allow}.
 */
public final class RobotsRules {

    /** Token do agente procurado no robots.txt. */
    public static final String AGENT_TOKEN = "jhcomp-prospect";

    private static final RobotsRules ALLOW_ALL = new RobotsRules(List.of());
    private static final RobotsRules DISALLOW_ALL = new RobotsRules(List.of(new Rule(false, "/")));

    /**
     * Limites para conteudo externo. robots.txt e escrito por terceiros: sem
     * teto, milhares de regras longas cheias de curinga ocupariam o worker.
     */
    static final int MAX_RULES = 500;
    static final int MAX_RULE_LENGTH = 512;
    static final int MAX_PATH_LENGTH = 2048;

    private record Rule(boolean allow, String path) {}

    private final List<Rule> rules;

    private RobotsRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** robots.txt inexistente (4xx): tudo permitido (RFC 9309 §2.3.1.3). */
    public static RobotsRules allowAll() {
        return ALLOW_ALL;
    }

    /** robots.txt inacessivel por erro do servidor (5xx): nada permitido (RFC 9309 §2.3.1.4). */
    public static RobotsRules disallowAll() {
        return DISALLOW_ALL;
    }

    public static RobotsRules parse(String content) {
        List<Rule> specific = new ArrayList<>();
        List<Rule> wildcard = new ArrayList<>();
        boolean specificGroupSeen = false;

        List<String> agents = new ArrayList<>();
        boolean readingAgents = false;

        for (String rawLine : content.split("\\r?\\n|\\r")) {
            String line = stripComment(rawLine).strip();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();

            if (key.equals("user-agent")) {
                if (!readingAgents) {
                    agents.clear();
                    readingAgents = true;
                }
                agents.add(value.toLowerCase(Locale.ROOT));
                continue;
            }
            readingAgents = false;
            if (!key.equals("allow") && !key.equals("disallow")) {
                continue;
            }
            // Disallow vazio nao proibe nada.
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > MAX_RULE_LENGTH || specific.size() + wildcard.size() >= MAX_RULES) {
                continue;
            }
            Rule rule = new Rule(key.equals("allow"), value);
            if (agents.stream().anyMatch(agent -> agent.contains(AGENT_TOKEN))) {
                specific.add(rule);
                specificGroupSeen = true;
            }
            if (agents.contains("*")) {
                wildcard.add(rule);
            }
        }
        return new RobotsRules(List.copyOf(specificGroupSeen ? specific : wildcard));
    }

    public boolean allows(String path) {
        // Caminho alem do limite nao e comparado: sem decisao segura, nao le.
        if (path.length() > MAX_PATH_LENGTH) {
            return false;
        }
        Rule best = null;
        for (Rule rule : rules) {
            if (!matches(rule.path(), path)) {
                continue;
            }
            if (best == null
                    || rule.path().length() > best.path().length()
                    || (rule.path().length() == best.path().length() && rule.allow())) {
                best = rule;
            }
        }
        return best == null || best.allow();
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    /**
     * Curinga do robots.txt: {@code *} casa qualquer sequencia e {@code $} no
     * fim ancora o final; sem {@code $}, a regra casa como prefixo.
     *
     * <p>Sem expressao regular de proposito. Regras vem de terceiros, e uma
     * regex com varios {@code .*} pode levar tempo exponencial com backtracking
     * (ReDoS). Este comparador guarda so o ultimo curinga e volta no maximo
     * ate ele, como o parser de robots.txt do Google: tempo limitado por
     * tamanho da regra vezes tamanho do caminho, ambos com teto.
     */
    static boolean matches(String rule, String path) {
        boolean anchored = rule.endsWith("$");
        String pattern = anchored ? rule.substring(0, rule.length() - 1) : rule + "*";
        int p = 0;
        int s = 0;
        int star = -1;
        int resume = 0;
        while (s < path.length()) {
            if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                resume = s;
            } else if (p < pattern.length() && pattern.charAt(p) == path.charAt(s)) {
                p++;
                s++;
            } else if (star >= 0) {
                p = star + 1;
                s = ++resume;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }
}
