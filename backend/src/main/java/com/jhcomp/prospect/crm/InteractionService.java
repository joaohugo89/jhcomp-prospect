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
import com.jhcomp.prospect.shared.text.Texts;

/**
 * A lead's interactions (RF-041).
 *
 * <p>Recording an interaction does not change the lead's status: a recorded
 * contact does not move it to {@code CONTACTED} on its own (product rule 6).
 * The exception is a do-not-contact request, which has to take effect
 * immediately (doc 06 §9).
 */
@Service
public class InteractionService {

    /** Tolerance for a browser clock that is slightly fast. */
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
     * Records the interaction.
     *
     * <p>An outbound approach to a blocked lead is refused (product rule 4):
     * the block applies to recording a contact, not only to generating a
     * message, and it includes the do-not-contact list. A contact the company
     * started is still recordable.
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
                Texts.blankToNull(input.outcome()),
                Texts.blankToNull(input.notes()),
                user.userId(),
                now));

        if (input.type() == InteractionType.DO_NOT_CONTACT_REQUEST) {
            // The do-not-contact list applies even to a won or lost lead,
            // which no longer changes status (D26).
            events.publishEvent(new DoNotContactRequested(lead.getOrganizationId(), lead.getId(), input.occurredAt()));
            if (lead.getStatus() != LeadStatus.DO_NOT_CONTACT
                    && LeadStatusTransitions.isAllowed(lead.getStatus(), LeadStatus.DO_NOT_CONTACT)) {
                leadService.changeStatus(lead.getId(), LeadStatus.DO_NOT_CONTACT, "Pedido de não contato registrado", false);
            }
        }
        return interaction;
    }

    @Transactional(readOnly = true)
    public Page<InteractionEntity> list(UUID leadId, Pageable pageable) {
        LeadEntity lead = leadService.find(leadId);
        return repository.findByOrganizationIdAndLeadIdOrderByOccurredAtDescIdAsc(
                lead.getOrganizationId(), lead.getId(), pageable);
    }
}
