package com.jhcomp.prospect.outreach.domain;

/**
 * A request to the AI provider.
 *
 * <p>Instructions and data arrive separately: the adapter must send them in
 * distinct parts of the prompt, so data is never read as an instruction
 * (doc 07 §7).
 */
public record MessageGenerationRequest(String promptVersion, String instructions, MessageContext context) {}
