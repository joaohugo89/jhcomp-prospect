package com.jhcomp.prospect.crm;

import java.util.List;

import com.jhcomp.prospect.lead.domain.LeadStatus;

/**
 * Etapas da pipeline padrao (doc 06 §3, D2, D25).
 *
 * <p>No codigo, e nao em tabela: cada etapa e um valor de {@link LeadStatus},
 * e uma tabela que espelhasse o enum seria segunda fonte de verdade a manter
 * sincronizada. Sem CRUD no MVP (D9); quando a pipeline precisar ser
 * configuravel, ai sim ganha tabela.
 *
 * <p>{@code DO_NOT_CONTACT} nao e etapa: e bloqueio, fora do fluxo comercial.
 */
public final class PipelineStages {

    /** Etapa da pipeline. {@code key} e o status do lead que ela representa. */
    public record Stage(LeadStatus key, String name, int position, boolean terminal) {}

    public static final List<Stage> DEFAULT = List.of(
            new Stage(LeadStatus.NEW, "Novo", 1, false),
            new Stage(LeadStatus.QUALIFIED, "Qualificado", 2, false),
            new Stage(LeadStatus.CONTACT_APPROVED, "Contato aprovado", 3, false),
            new Stage(LeadStatus.CONTACTED, "Contatado", 4, false),
            new Stage(LeadStatus.IN_CONVERSATION, "Em conversa", 5, false),
            new Stage(LeadStatus.PROPOSAL_SENT, "Proposta enviada", 6, false),
            new Stage(LeadStatus.WON, "Ganho", 7, true),
            new Stage(LeadStatus.LOST, "Perdido", 8, true));

    private PipelineStages() {
    }
}
