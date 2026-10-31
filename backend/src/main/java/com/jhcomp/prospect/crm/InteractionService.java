package com.jhcomp.prospect.crm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.domain.LeadStatusTransitions;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Interacoes do lead (RF-041).
 *
 * <p>Registrar interacao nao muda o status do lead: contato registrado nao
 * move para {@code CONTACTED} sozinho (regra 6 do produto). A excecao e o
 * pedido de nao contato, que precisa valer na hora (doc 06 §9).
 */
@Service
public class InteractionService {

    /** Tolerancia para relogio do navegador um pouco adiantado. */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private final InteractionRepository repository;
    private final LeadService leadService;
    private final ContactBlockCheck contactBlockCheck;
    private final ApplicationEventPublisher events;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    InteractionService(
            InteractionRepository repository,
            LeadService leadService,
            ContactBlockCheck contactBlockCheck,
            ApplicationEventPublisher events,
            OrganizationContext organizationContext,
            Clock clock) {
        this.repository = repository;
        this.leadService = leadService;
        this.contactBlockCheck = contactBlockCheck;
        this.events = events;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    public record NewInteraction(
            InteractionType type,
            InteractionChannel channel,
            InteractionDirection direction,
            Instant occurredAt,
            String outcome,
            String notes) {}

    /**
     * Registra a interacao.
     *
     * <p>Abordagem de saida a lead bloqueado e recusada (regra 4 do produto):
     * o bloqueio vale para registrar contato, nao so para gerar mensagem, e
     * inclui a lista de nao contato. Contato iniciado pela empresa continua
     * registravel.
     */
    @Transactional
    public InteractionEntity record(UUID leadId, NewInteraction input) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        LeadEntity lead = leadService.find(leadId);
        Instant now = clock.instant();

        if (input.occurredAt().isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new InvalidRequestException("occurredAt must not be in the future");
        }
        if (input.type().isApproach() && input.direction() == InteractionDirection.OUTBOUND
                && (lead.getStatus() == LeadStatus.DO_NOT_CONTACT || contactBlockCheck.isBlocked(lead, input.channel()))) {
            throw new ConflictException("Lead is marked as do not contact");
        }

        InteractionEntity interaction = repository.save(new InteractionEntity(
                lead.getOrganizationId(),
                lead.getId(),
                input.type(),
                input.channel(),
                input.direction(),
                input.occurredAt(),
                blankToNull(input.outcome()),
                blankToNull(input.notes()),
                user.userId(),
                now));

        if (input.type() == InteractionType.DO_NOT_CONTACT_REQUEST) {
            events.publishEvent(new DoNotContactRequested(lead.getOrganizationId(), lead.getId(), input.occurredAt()));
        }
        if (input.type() == InteractionType.DO_NOT_CONTACT_REQUEST
                && LeadStatusTransitions.isAllowed(lead.getStatus(), LeadStatus.DO_NOT_CONTACT)
                && lead.getStatus() != LeadStatus.DO_NOT_CONTACT) {
            leadService.changeStatus(lead.getId(), LeadStatus.DO_NOT_CONTACT, "Pedido de não contato registrado", false);
        }
        return interaction;
    }

    @Transactional(readOnly = true)
    public Page<InteractionEntity> list(UUID leadId, Pageable pageable) {
        LeadEntity lead = leadService.find(leadId);
        return repository.findByOrganizationIdAndLeadIdOrderByOccurredAtDescIdAsc(
                lead.getOrganizationId(), lead.getId(), pageable);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
