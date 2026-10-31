package com.jhcomp.prospect.outreach.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.crm.ContactBlockCheck;
import com.jhcomp.prospect.crm.DoNotContactRequested;
import com.jhcomp.prospect.crm.InteractionChannel;
import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.domain.LeadStatusChanged;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.outreach.domain.ContactIdentifier;
import com.jhcomp.prospect.outreach.domain.ContactIdentifiers;
import com.jhcomp.prospect.outreach.domain.IdentifierType;
import com.jhcomp.prospect.outreach.domain.OutreachChannel;
import com.jhcomp.prospect.outreach.infrastructure.persistence.SuppressionEntryEntity;
import com.jhcomp.prospect.outreach.infrastructure.persistence.SuppressionEntryRepository;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditOutcome;
import com.jhcomp.prospect.shared.audit.AuditService;
import com.jhcomp.prospect.shared.audit.AuditService.AuditEntry;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/**
 * Lista de nao contato (RF-053, doc 06 §9, doc 07 §10, D26).
 *
 * <p>O bloqueio vale pelo identificador, nao pelo lead: empresa reimportada
 * com o mesmo telefone continua bloqueada, e importacao nunca remove entrada.
 * A conferencia e conservadora: qualquer identificador da empresa bloqueado
 * no canal, ou em todos, bloqueia a abordagem.
 */
@Service
public class SuppressionService implements ContactBlockCheck {

    static final String LEAD_REQUEST_REASON = "Pedido de não contato registrado no lead";

    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    private final SuppressionEntryRepository repository;
    private final LeadService leadService;
    private final AuditService auditService;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    SuppressionService(
            SuppressionEntryRepository repository,
            LeadService leadService,
            AuditService auditService,
            OrganizationContext organizationContext,
            Clock clock) {
        this.repository = repository;
        this.leadService = leadService;
        this.auditService = auditService;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    /**
     * Registra o pedido. Idempotente: o mesmo identificador no mesmo escopo
     * devolve a entrada existente, sem duplicar. Sem {@code requestedAt},
     * vale o instante do registro.
     */
    @Transactional
    public SuppressionEntryEntity register(
            IdentifierType type, String rawIdentifier, OutreachChannel channel, String reason, Instant requestedAt) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        Instant now = clock.instant();
        Instant requested = requestedAt == null ? now : requestedAt;
        if (requested.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new InvalidRequestException("requestedAt must not be in the future");
        }
        ContactIdentifier identifier = ContactIdentifiers.normalize(type, rawIdentifier)
                .orElseThrow(() -> new InvalidRequestException("Identifier is not a valid " + type));
        return store(user, identifier, channel, reason.strip(), requested, now);
    }

    @Transactional(readOnly = true)
    public Page<SuppressionEntryEntity> list(Pageable pageable) {
        return repository.findByOrganizationIdOrderByCreatedAtDescIdAsc(organizationContext.requireOrganizationId(), pageable);
    }

    /** Lead em {@code DO_NOT_CONTACT} ou com identificador bloqueado no canal. */
    @Transactional(readOnly = true)
    public boolean isBlocked(LeadEntity lead, OutreachChannel channel) {
        if (lead.getStatus() == LeadStatus.DO_NOT_CONTACT) {
            return true;
        }
        List<ContactIdentifier> identifiers = identifiersOf(lead.getCompany());
        if (identifiers.isEmpty()) {
            return false;
        }
        return repository.findByOrganizationIdAndIdentifierIn(
                        lead.getOrganizationId(), identifiers.stream().map(ContactIdentifier::value).toList())
                .stream()
                .filter(entry -> identifiers.contains(new ContactIdentifier(entry.getIdentifierType(), entry.getIdentifier())))
                .anyMatch(entry -> entry.getChannel() == null || channel == null || entry.getChannel() == channel);
    }

    /** Canal do CRM sem equivalente (presencial, outro) e conferido contra todos. */
    @Override
    public boolean isBlocked(LeadEntity lead, InteractionChannel channel) {
        return isBlocked(lead, switch (channel) {
            case PHONE -> OutreachChannel.PHONE_CALL;
            case WHATSAPP -> OutreachChannel.WHATSAPP;
            case EMAIL -> OutreachChannel.EMAIL;
            case INSTAGRAM -> OutreachChannel.INSTAGRAM;
            case IN_PERSON, OTHER -> null;
        });
    }

    /** Identificador do destinatario no canal, se a empresa tiver um valido. */
    public static Optional<ContactIdentifier> recipientOf(CompanyEntity company, OutreachChannel channel) {
        return identifiersOf(company).stream()
                .filter(identifier -> identifier.type() == channel.recipientType())
                .findFirst();
    }

    static List<ContactIdentifier> identifiersOf(CompanyEntity company) {
        List<ContactIdentifier> identifiers = new ArrayList<>();
        ContactIdentifiers.normalize(IdentifierType.PHONE, company.getPhone()).ifPresent(identifiers::add);
        ContactIdentifiers.normalize(IdentifierType.EMAIL, company.getBusinessEmail()).ifPresent(identifiers::add);
        ContactIdentifiers.normalize(IdentifierType.INSTAGRAM, company.getInstagramUrl()).ifPresent(identifiers::add);
        return identifiers;
    }

    /**
     * Pedido registrado no lead bloqueia todos os identificadores da empresa
     * em todos os canais, na mesma transacao do pedido (doc 06 §9).
     */
    @EventListener
    void onDoNotContactRequested(DoNotContactRequested event) {
        suppressCompanyOf(event.organizationId(), event.leadId(), event.requestedAt());
    }

    @EventListener
    void onStatusChanged(LeadStatusChanged event) {
        if (event.to() == LeadStatus.DO_NOT_CONTACT) {
            suppressCompanyOf(event.organizationId(), event.leadId(), clock.instant());
        }
    }

    private void suppressCompanyOf(UUID organizationId, UUID leadId, Instant requestedAt) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        Instant now = clock.instant();
        leadService.findInOrganization(organizationId, leadId).ifPresent(lead -> identifiersOf(lead.getCompany())
                .forEach(identifier -> store(user, identifier, null, LEAD_REQUEST_REASON, requestedAt, now)));
    }

    private SuppressionEntryEntity store(
            AuthenticatedUser user,
            ContactIdentifier identifier,
            OutreachChannel channel,
            String reason,
            Instant requestedAt,
            Instant now) {
        Optional<SuppressionEntryEntity> existing = repository
                .findByOrganizationIdAndIdentifierIn(user.organizationId(), List.of(identifier.value()))
                .stream()
                .filter(entry -> entry.getIdentifierType() == identifier.type() && entry.getChannel() == channel)
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        SuppressionEntryEntity entry = repository.save(new SuppressionEntryEntity(
                user.organizationId(), identifier, channel, reason, requestedAt, user.userId(), now));
        // Sem o identificador no detalhe: a trilha nao precisa repetir o dado pessoal.
        auditService.record(new AuditEntry(user.organizationId(), user.userId(), AuditAction.SUPPRESSION_CREATE,
                AuditOutcome.SUCCESS, "SuppressionEntry", entry.getId().toString(), null,
                channel == null ? "ALL_CHANNELS" : channel.name()));
        return entry;
    }
}
