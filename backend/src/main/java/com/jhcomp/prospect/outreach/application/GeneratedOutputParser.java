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
 * Valida a saida da IA antes de virar rascunho (regra 9, doc 07 §7).
 *
 * <p>Estrutura estrita: objeto com {@code body} texto, {@code subject} texto
 * ou nulo e {@code unverifiedClaims} lista de textos. Campo desconhecido,
 * tipo errado ou tamanho acima do limite recusa a saida inteira; aproveitar
 * parte de uma resposta malformada seria confiar justamente no que falhou.
 */
@Component
class GeneratedOutputParser {

    /** Teto antes de interpretar: protege o parser de resposta desproporcional. */
    static final int MAX_RAW_LENGTH = 20_000;

    private static final Set<String> FIELDS = Set.of("subject", "body", "unverifiedClaims");

    private final ObjectMapper objectMapper;

    GeneratedOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Rascunho valido, ou o motivo da recusa (sem o conteudo, que nao vai para log). */
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
