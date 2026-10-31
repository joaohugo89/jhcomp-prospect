package com.jhcomp.prospect.outreach.domain;

/**
 * Provedor de IA (doc 03 §6, D26).
 *
 * <p>Unica capacidade: devolver texto. Nao recebe repositorio, servico nem
 * cliente de envio, e nao ha metodo que grave, envie, publique ou remova
 * bloqueio (doc 06 §11, regra 1 do produto). Um teste estrutural garante que
 * as implementacoes continuem assim.
 *
 * <p>Implementacoes que chamam a rede aplicam timeout e tratam limite de
 * taxa do fornecedor; falha vira {@link AIProviderException}.
 */
public interface AIProvider {

    AIResponse generateMessage(MessageGenerationRequest request);
}
