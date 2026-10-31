package com.jhcomp.prospect.outreach.domain;

/**
 * Pedido ao provedor de IA.
 *
 * <p>Instrucoes e dados chegam separados: o adaptador deve envia-los em
 * partes distintas do prompt, para que dado nunca seja lido como instrucao
 * (doc 07 §7).
 */
public record MessageGenerationRequest(String promptVersion, String instructions, MessageContext context) {}
