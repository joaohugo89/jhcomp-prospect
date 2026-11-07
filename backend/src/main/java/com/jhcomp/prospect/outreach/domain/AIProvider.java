package com.jhcomp.prospect.outreach.domain;

/**
 * The AI provider (doc 03 §6, D26).
 *
 * <p>A single capability: returning text. It receives no repository, no
 * service and no sending client, and there is no method that writes, sends,
 * publishes or removes a block (doc 06 §11, product rule 1). A structural test
 * keeps implementations that way.
 *
 * <p>Implementations that call the network apply a timeout and handle the
 * provider's rate limit; a failure becomes {@link AIProviderException}.
 */
public interface AIProvider {

    AIResponse generateMessage(MessageGenerationRequest request);
}
