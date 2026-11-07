package com.jhcomp.prospect.outreach.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.jhcomp.prospect.outreach.domain.MessageDraft;
import com.jhcomp.prospect.outreach.domain.OutreachChannel;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates the AI's output before it becomes a draft (rule 9, doc 07 §7).
 *
 * <p>A strict structure: an object with {@code body} as text, {@code subject}
 * as text or null, and {@code unverifiedClaims} as a list of strings. An
 * unknown field, a wrong type or a size over the limit refuses the whole
 * output; salvaging part of a malformed response would mean trusting exactly
 * what failed.
 */
@Component
class GeneratedOutputParser {

    /** A ceiling before parsing: it protects the parser from a disproportionate response. */
    static final int MAX_RAW_LENGTH = 20_000;

    private static final Set<String> FIELDS = Set.of("subject", "body", "unverifiedClaims");

    private final ObjectMapper objectMapper;

    GeneratedOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** A valid draft, or the reason for refusal — without the content, which never reaches a log. */
    MessageDraft.Result parse(OutreachChannel channel, String raw) {
        if (raw == null || raw.isBlank()) {
            return invalid("empty output");
        }
        if (raw.length() > MAX_RAW_LENGTH) {
            return invalid("output too large");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(raw.strip());
        } catch (JacksonException exception) {
            return invalid("output is not JSON");
        }
        if (root == null || !root.isObject()) {
            return invalid("output is not a JSON object");
        }
        for (String name : root.propertyNames()) {
            if (!FIELDS.contains(name)) {
                return invalid("unexpected field");
            }
        }

        JsonNode body = root.get("body");
        JsonNode subject = root.get("subject");
        JsonNode claims = root.get("unverifiedClaims");
        if (body == null || !body.isString()) {
            return invalid("body must be a string");
        }
        if (subject != null && !subject.isNull() && !subject.isString()) {
            return invalid("subject must be a string or null");
        }
        if (claims != null && !claims.isArray()) {
            return invalid("unverifiedClaims must be an array");
        }

        List<String> claimTexts = new ArrayList<>();
        if (claims != null) {
            for (JsonNode claim : claims) {
                if (!claim.isString()) {
                    return invalid("unverifiedClaims must contain strings");
                }
                claimTexts.add(claim.stringValue());
            }
        }
        String subjectText = subject == null || subject.isNull() ? null : subject.stringValue();
        return MessageDraft.of(channel, subjectText, body.stringValue(), claimTexts);
    }

    private static MessageDraft.Result invalid(String reason) {
        return MessageDraft.Result.invalid(reason);
    }
}
