package com.jhcomp.prospect.analysis.infrastructure.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The rules of a robots.txt (RFC 9309), only as much as is needed to decide
 * whether a path may be read (doc 06 §6).
 *
 * <p>It uses our agent's group when there is one, otherwise the {@code *}
 * group. Among the matching rules the longest wins; a tie favours
 * {@code Allow}.
 */
public final class RobotsRules {

    /** The agent token looked for in robots.txt. */
    public static final String AGENT_TOKEN = "jhcomp-prospect";

    private static final RobotsRules ALLOW_ALL = new RobotsRules(List.of());
    private static final RobotsRules DISALLOW_ALL = new RobotsRules(List.of(new Rule(false, "/")));

    /**
     * Limits for external content. robots.txt is written by third parties:
     * without a ceiling, thousands of long rules full of wildcards would
     * occupy the worker.
     */
    static final int MAX_RULES = 500;
    static final int MAX_RULE_LENGTH = 512;
    static final int MAX_PATH_LENGTH = 2048;

    private record Rule(boolean allow, String path) {}

    private final List<Rule> rules;

    private RobotsRules(List<Rule> rules) {
        this.rules = rules;
    }

    /** No robots.txt (4xx): everything is allowed (RFC 9309 §2.3.1.3). */
    public static RobotsRules allowAll() {
        return ALLOW_ALL;
    }

    /** robots.txt unreachable through a server error (5xx): nothing is allowed (RFC 9309 §2.3.1.4). */
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
            // An empty Disallow forbids nothing.
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
        // A path beyond the limit is not matched: with no safe decision, it does not read.
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
     * robots.txt wildcards: {@code *} matches any sequence and a trailing
     * {@code $} anchors the end; without {@code $}, a rule matches as a
     * prefix.
     *
     * <p>Deliberately without regular expressions. The rules come from third
     * parties, and a regex with several {@code .*} can take exponential time
     * through backtracking (ReDoS). This matcher remembers only the last
     * wildcard and backtracks at most to it, like Google's robots.txt parser:
     * time is bounded by rule length times path length, both capped.
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
