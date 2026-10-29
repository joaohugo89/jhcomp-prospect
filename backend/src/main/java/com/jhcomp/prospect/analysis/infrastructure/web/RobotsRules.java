package com.jhcomp.prospect.analysis.infrastructure.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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
    private static final RobotsRules DISALLOW_ALL = new RobotsRules(List.of(new Rule(false, "/", Pattern.compile("^/"))));

    private record Rule(boolean allow, String path, Pattern pattern) {}

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
            Rule rule = new Rule(key.equals("allow"), value, toPattern(value));
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
        Rule best = null;
        for (Rule rule : rules) {
            if (!rule.pattern().matcher(path).find()) {
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

    /** {@code *} casa qualquer sequencia; {@code $} no fim ancora o final. */
    private static Pattern toPattern(String path) {
        boolean anchored = path.endsWith("$");
        String body = anchored ? path.substring(0, path.length() - 1) : path;
        StringBuilder regex = new StringBuilder("^");
        for (String part : body.split("\\*", -1)) {
            if (regex.length() > 1) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        if (anchored) {
            regex.append('$');
        }
        return Pattern.compile(regex.toString());
    }
}
