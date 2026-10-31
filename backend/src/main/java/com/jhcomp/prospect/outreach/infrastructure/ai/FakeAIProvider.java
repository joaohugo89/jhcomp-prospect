package com.jhcomp.prospect.outreach.infrastructure.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.jhcomp.prospect.outreach.domain.AIProvider;
import com.jhcomp.prospect.outreach.domain.AIResponse;
import com.jhcomp.prospect.outreach.domain.MessageContext;
import com.jhcomp.prospect.outreach.domain.MessageGenerationRequest;
import com.jhcomp.prospect.outreach.domain.OutreachChannel;

import tools.jackson.databind.ObjectMapper;

/**
 * Provedor deterministico, sem rede (D26).
 *
 * <p>E o unico provedor do MVP e o usado nos testes. Monta o texto so com o
 * contexto recebido e devolve o mesmo formato JSON que um modelo devolveria,
 * para que a validacao de saida seja exercitada de verdade. Sem achados
 * confirmados, a mensagem fala de presenca digital em termos gerais e marca
 * isso como afirmacao nao verificada, levando o rascunho a revisao.
 */
@Component
@ConditionalOnProperty(name = "jhcomp.outreach.ai.provider", havingValue = "fake", matchIfMissing = true)
class FakeAIProvider implements AIProvider {

    static final String GENERATED_BY = "fake:template-v1";

    private final ObjectMapper objectMapper;

    FakeAIProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AIResponse generateMessage(MessageGenerationRequest request) {
        MessageContext context = request.context();
        List<String> claims = new ArrayList<>();

        StringBuilder body = new StringBuilder();
        body.append(context.channel() == OutreachChannel.PHONE_CALL ? "Roteiro: " : "")
                .append("Olá, equipe da ").append(context.companyName()).append(". ")
                .append("Sou ").append(context.senderName()).append(", da ").append(context.senderOrganization())
                .append(", e trabalho com presença digital para empresas")
                .append(context.city() == null ? "" : " de " + context.city())
                .append(".\n\n");

        if (context.confirmedFindings().isEmpty()) {
            body.append("Entro em contato porque acredito que podemos ajudar a fortalecer a presença online de vocês.");
            claims.add("A mensagem sugere oportunidade de melhoria sem achado confirmado na análise.");
        } else {
            body.append("Ao verificar a presença online de vocês, observamos:\n");
            context.confirmedFindings().forEach(finding -> body.append("- ").append(finding).append('\n'));
            body.append("\nPodemos ajudar com isso.");
        }
        body.append("\n\nSe fizer sentido, posso explicar sem compromisso. ")
                .append("Se preferir não receber novos contatos, é só responder e não voltaremos a escrever.");

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("subject", context.channel().allowsSubject() ? "Presença digital da " + context.companyName() : null);
        output.put("body", body.toString());
        output.put("unverifiedClaims", claims);
        return new AIResponse(GENERATED_BY, objectMapper.writeValueAsString(output));
    }
}
