package com.jhcomp.prospect.outreach.domain;

/**
 * Resposta bruta do provedor.
 *
 * <p>{@code content} e dado nao confiavel (regra 9 do produto): so vira
 * rascunho depois de passar pela validacao de estrutura e tamanho.
 * {@code generatedBy} identifica provedor e modelo, sem segredo.
 */
public record AIResponse(String generatedBy, String content) {}
