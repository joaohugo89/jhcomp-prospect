package com.jhcomp.prospect.outreach.domain;

/**
 * The provider's raw response.
 *
 * <p>{@code content} is untrusted data (product rule 9): it only becomes a
 * draft after passing structure and size validation. {@code generatedBy}
 * identifies the provider and model, with no secret in it.
 */
public record AIResponse(String generatedBy, String content) {}
