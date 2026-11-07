package com.jhcomp.prospect.outreach.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * The do-not-contact list (RF-053, doc 06 §9, doc 07 §10, D26).
 *
 * <p>A block applies to the identifier, not to the lead: a company re-imported
 * with the same phone stays blocked, and an import never removes an entry. The
 * check is conservative: any identifier of the company blocked on the channel,
 * or on all of them, blocks the approach.
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
     * Records the request. Idempotent: the same identifier in the same scope
     * returns the existing entry rather than duplicating it. Without
     * {@code requestedAt}, the moment of recording is used.
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

    /**
     * The administrator's listing. {@code type} narrows by type;
     * {@code search} is a contact in any format, normalised the same way as on
     * registration (D10): "(35) 9 8888-0001" finds "+5535988880001". A search
     * that normalises under no type finds nothing, rather than silently
     * becoming "no filter".
     */
    @Transactional(readOnly = true)
    public Page<SuppressionEntryEntity> list(IdentifierType type, String search, Pageable pageable) {
        UUID organizationId = organizationContext.requireOrganizationId();
        Set<IdentifierType> types = type == null ? EnumSet.allOf(IdentifierType.class) : EnumSet.of(type);
        if (search != null && search.length() > 320) {
            throw new InvalidRequestException("search must have at most 320 characters");
        }
        if (search == null || search.isBlank()) {
            return type == null
                    ? repository.findByOrganizationIdOrderByCreatedAtDescIdAsc(organizationId, pageable)
                    : repository.findByOrganizationIdAndIdentifierTypeInOrderByCreatedAtDescIdAsc(
                            organizationId, types, pageable);
        }
        List<String> values = types.stream()
                .flatMap(candidate -> ContactIdentifiers.normalize(candidate, search).stream())
                .map(ContactIdentifier::value)
                .distinct()
                .toList();
        if (values.isEmpty()) {
            return Page.empty(pageable);
        }
        return repository.findByOrganizationIdAndIdentifierTypeInAndIdentifierInOrderByCreatedAtDescIdAsc(
                organizationId, types, values, pageable);
    }

    /**
     * Totals per identifier type, for the top of the screen.
     *
     * <p>One grouped query, not one per type. A type with no entries is absent
     * from the result set, so the map starts at zero for every constant: the
     * screen must show "0", not omit the row.
     */
    @Transactional(readOnly = true)
    public Map<IdentifierType, Long> countsByType() {
        UUID organizationId = organizationContext.requireOrganizationId();
        Map<IdentifierType, Long> counts = new EnumMap<>(IdentifierType.class);
        for (IdentifierType type : IdentifierType.values()) {
            counts.put(type, 0L);
        }
        for (SuppressionEntryRepository.IdentifierTypeCount row : repository.countGroupedByIdentifierType(
                organizationId)) {
            counts.put(row.getIdentifierType(), row.getTotal());
        }
        return counts;
    }

    /**
     * The result of a check: only whether it is blocked, and on which
     * channels. It returns no reason, date or author: whoever checks needs to
     * know whether they may approach, not the history of the request.
     */
    public record CheckResult(boolean recognized, boolean blocked, boolean allChannels, Set<OutreachChannel> channels) {}

    /** Any member can check a contact before approaching from outside the system (rule 4). */
    @Transactional(readOnly = true)
    public CheckResult check(IdentifierType type, String rawIdentifier) {
        UUID organizationId = organizationContext.requireOrganizationId();
        Optional<ContactIdentifier> identifier = ContactIdentifiers.normalize(type, rawIdentifier);
        if (identifier.isEmpty()) {
            return new CheckResult(false, false, false, Set.of());
        }
        List<SuppressionEntryEntity> entries = repository
                .findByOrganizationIdAndIdentifierIn(organizationId, List.of(identifier.get().value()))
                .stream()
                .filter(entry -> entry.getIdentifierType() == type)
                .toList();
        boolean all = entries.stream().anyMatch(entry -> entry.getChannel() == null);
        Set<OutreachChannel> channels = EnumSet.noneOf(OutreachChannel.class);
        entries.stream().map(SuppressionEntryEntity::getChannel).filter(Objects::nonNull).forEach(channels::add);
        return new CheckResult(true, !entries.isEmpty(), all, all ? Set.of() : Set.copyOf(channels));
    }

    /**
     * A lead in {@code DO_NOT_CONTACT}, or with an identifier blocked on the
     * channel. A null channel — an approach with no outreach channel — is
     * checked against all of them.
     */
    @Transactional(readOnly = true)
    public boolean isBlocked(LeadEntity lead, OutreachChannel channel) {
        Set<OutreachChannel> blocked = blockedChannels(lead);
        return channel == null ? !blocked.isEmpty() : blocked.contains(channel);
    }

    /**
     * The channels blocked for the lead's company, in a single query. An entry
     * with no channel, or a lead in {@code DO_NOT_CONTACT}, blocks all of
     * them.
     */
    @Transactional(readOnly = true)
    public Set<OutreachChannel> blockedChannels(LeadEntity lead) {
        if (lead.getStatus() == LeadStatus.DO_NOT_CONTACT) {
            return EnumSet.allOf(OutreachChannel.class);
        }
        List<ContactIdentifier> identifiers = identifiersOf(lead.getCompany());
        if (identifiers.isEmpty()) {
            return EnumSet.noneOf(OutreachChannel.class);
        }
        Set<OutreachChannel> blocked = EnumSet.noneOf(OutreachChannel.class);
        for (SuppressionEntryEntity entry : repository.findByOrganizationIdAndIdentifierIn(
                lead.getOrganizationId(), identifiers.stream().map(ContactIdentifier::value).toList())) {
            if (!identifiers.contains(new ContactIdentifier(entry.getIdentifierType(), entry.getIdentifier()))) {
                continue;
            }
            if (entry.getChannel() == null) {
                return EnumSet.allOf(OutreachChannel.class);
            }
            blocked.add(entry.getChannel());
        }
        return blocked;
    }

    /** A CRM channel with no equivalent (in person, other) is checked against all of them. */
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

    /** The recipient's identifier on the channel, when the company has a valid one. */
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
     * A request recorded on the lead blocks every identifier of the company on
     * every channel, in the same transaction as the request (doc 06 §9).
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
        // No identifier in the detail: the trail does not need to repeat the personal data.
        auditService.record(new AuditEntry(user.organizationId(), user.userId(), AuditAction.SUPPRESSION_CREATE,
                AuditOutcome.SUCCESS, "SuppressionEntry", entry.getId().toString(), null,
                channel == null ? "ALL_CHANNELS" : channel.name()));
        return entry;
    }
}
