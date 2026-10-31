package com.jhcomp.prospect.outreach.domain;

import java.util.List;

/**
 * Tudo o que a IA pode usar para redigir a mensagem (doc 06 §8, D26).
 *
 * <p>Contem apenas dados da empresa e achados confirmados; telefone, e-mail,
 * Instagram, notas do operador e conteudo de pagina externa ficam de fora de
 * proposito (doc 07 §7). Os achados sao descricoes fixas do sistema, nunca
 * texto vindo de fora.
 */
public record MessageContext(
        OutreachChannel channel,
        String senderOrganization,
        String senderName,
        String companyName,
        String segment,
        String city,
        String state,
        List<String> confirmedFindings) {

    public MessageContext {
        confirmedFindings = List.copyOf(confirmedFindings);
    }
}
