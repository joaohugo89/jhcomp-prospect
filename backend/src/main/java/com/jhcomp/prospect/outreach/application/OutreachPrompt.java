package com.jhcomp.prospect.outreach.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/**
 * The versioned prompt for message generation (D26).
 *
 * <p>The text lives in a resource and the version is stored on every message,
 * so it is possible to know later which instruction produced a draft.
 * Changing the text requires a new version, never editing the existing
 * one.
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
