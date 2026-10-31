package com.jhcomp.prospect.outreach.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/**
 * Prompt versionado da geracao de mensagens (D26).
 *
 * <p>O texto vive em resource e a versao vai gravada em cada mensagem, para
 * saber depois com que instrucao um rascunho foi gerado. Mudar o texto exige
 * nova versao, nunca editar a existente.
 */
@Component
class OutreachPrompt {

    static final String VERSION = "outreach-v1";

    private final String instructions;

    OutreachPrompt() {
        try (InputStream stream = OutreachPrompt.class.getResourceAsStream("/prompts/" + VERSION + ".txt")) {
            if (stream == null) {
                throw new IllegalStateException("Prompt resource not found: " + VERSION);
            }
            this.instructions = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    String instructions() {
        return instructions;
    }
}
