package com.jhcomp.prospect.outreach.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.jhcomp.prospect.outreach.domain.MessageDraft;
import com.jhcomp.prospect.outreach.domain.OutreachChannel;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("Saida da IA: validacao estrita antes de virar rascunho")
class GeneratedOutputParserTest {

    private final GeneratedOutputParser parser = new GeneratedOutputParser(JsonMapper.builder().build());

    @Test
    @DisplayName("aceita objeto com corpo, assunto e afirmacoes")
    void acceptsValidOutput() {
        MessageDraft draft = parser.parse(OutreachChannel.EMAIL, """
                {"subject": "Oi", "body": "Corpo", "unverifiedClaims": ["algo"]}
                """).draft().orElseThrow();

        assertThat(draft.subject()).isEqualTo("Oi");
        assertThat(draft.body()).isEqualTo("Corpo");
        assertThat(draft.unverifiedClaims()).containsExactly("algo");
    }

    @Test
    @DisplayName("assunto e afirmacoes sao opcionais")
    void optionalFields() {
        assertThat(parser.parse(OutreachChannel.WHATSAPP, "{\"body\": \"Corpo\", \"subject\": null}").draft()).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "texto livre, nao JSON",
            "[\"body\"]",
            "\"so uma string\"",
            "{\"subject\": \"x\"}",
            "{\"body\": 42}",
            "{\"body\": \"ok\", \"subject\": 1}",
            "{\"body\": \"ok\", \"unverifiedClaims\": \"nao e lista\"}",
            "{\"body\": \"ok\", \"unverifiedClaims\": [1]}",
            "{\"body\": \"ok\", \"send\": true}",
            "{\"body\": \"\"}",
            "{\"body\": \"ok\"} trailing"
    })
    @DisplayName("recusa saida malformada inteira")
    void rejectsMalformedOutput(String raw) {
        assertThat(parser.parse(OutreachChannel.EMAIL, raw).draft()).isEmpty();
    }

    @Test
    @DisplayName("recusa resposta acima do teto antes de interpretar")
    void rejectsOversizedOutput() {
        String raw = "{\"body\": \"" + "a".repeat(GeneratedOutputParser.MAX_RAW_LENGTH) + "\"}";

        assertThat(parser.parse(OutreachChannel.EMAIL, raw).violation()).isEqualTo("output too large");
    }

    @Test
    @DisplayName("aplica o limite do canal a saida da IA")
    void appliesChannelLimit() {
        String raw = "{\"body\": \"" + "a".repeat(OutreachChannel.WHATSAPP.maxBodyLength() + 1) + "\"}";

        assertThat(parser.parse(OutreachChannel.WHATSAPP, raw).draft()).isEmpty();
    }
}
