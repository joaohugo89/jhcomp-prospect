package com.jhcomp.prospect.crm;

import java.util.List;

import com.jhcomp.prospect.lead.domain.LeadStatus;

/**
 * The default pipeline's stages (doc 06 §3, D2, D25).
 *
 * <p>In code, not in a table: each stage is a {@link LeadStatus} value, and a
 * table mirroring the enum would be a second source of truth to keep in sync.
 * No CRUD in the MVP (D9); when the pipeline has to become configurable, that
 * is when it gets a table.
 *
 * <p>{@code DO_NOT_CONTACT} is not a stage: it is a block, outside the
 * commercial flow.
 */
public final class PipelineStages {

    /** A pipeline stage. {@code key} is the lead status it represents. */
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
