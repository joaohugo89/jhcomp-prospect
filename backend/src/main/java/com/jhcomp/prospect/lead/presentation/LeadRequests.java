package com.jhcomp.prospect.lead.presentation;

import java.util.UUID;

import com.jhcomp.prospect.company.presentation.SourceRequest;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Requisicoes do modulo de lead (doc 05 §5). Nenhuma carrega organizacao. */
public final class LeadRequests {

    private LeadRequests() {
    }

    /** Prioridade ausente vira {@code MEDIUM}; origem ausente, cadastro manual. */
    public record CreateLeadRequest(@NotNull UUID companyId, LeadPriority priority, @Valid SourceRequest source) {}

    /**
     * Mudanca de status.
     *
     * @param contactConfirmed obrigatorio para ir a {@code CONTACTED}: o operador
     *     confirma que o contato de fato ocorreu (doc 06 §2). {@code Boolean}, e
     *     nao primitivo: o Jackson 3 recusa primitivo ausente no corpo, e
     *     ausente aqui significa "nao confirmado"
     */
    public record ChangeStatusRequest(
            @NotNull LeadStatus status, @Size(max = 500) String reason, Boolean contactConfirmed) {

        public boolean isContactConfirmed() {
            return Boolean.TRUE.equals(contactConfirmed);
        }
    }

    public record ChangePriorityRequest(@NotNull LeadPriority priority) {}

    /** Dados editaveis do lead. {@code assignedUserId} nulo remove o responsavel. */
    public record UpdateLeadRequest(UUID assignedUserId) {}
}
