package com.jhcomp.prospect.lead.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.company.application.CompanyService;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceEntity.SourceData;
import com.jhcomp.prospect.identity.application.MemberQueryService;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.domain.LeadStatusChanged;
import com.jhcomp.prospect.lead.domain.LeadStatusTransitions;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadRepository;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadSpecifications;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadStatusChangeEntity;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadStatusChangeRepository;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadTimelineQuery;
import com.jhcomp.prospect.shared.error.ConflictException;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;
import com.jhcomp.prospect.shared.text.Texts;

/**
 * Lead use cases (RF-011, RF-012, RF-013; doc 06 §2).
 *
 * <p>{@code Lead.status} is the single source of commercial state (D2). Every
 * status change records the actor, the date and the previous state in the same
 * transaction.
 */
@Service
public class LeadService {

    /** Newest first, with the id as tiebreak so pagination is stable. */
    public static final Sort DEFAULT_SORT =
            Sort.by(Sort.Direction.DESC, LeadEntity::getCreatedAt).and(Sort.by(LeadEntity::getId));

    private final LeadRepository leadRepository;
    private final LeadStatusChangeRepository statusChangeRepository;
    private final LeadTimelineQuery timelineQuery;
    private final CompanyService companyService;
    private final MemberQueryService memberQueryService;
    private final OrganizationContext organizationContext;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    LeadService(
            LeadRepository leadRepository,
            LeadStatusChangeRepository statusChangeRepository,
            LeadTimelineQuery timelineQuery,
            CompanyService companyService,
            MemberQueryService memberQueryService,
            OrganizationContext organizationContext,
            ApplicationEventPublisher events,
            Clock clock) {
        this.leadRepository = leadRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.timelineQuery = timelineQuery;
        this.companyService = companyService;
        this.memberQueryService = memberQueryService;
        this.organizationContext = organizationContext;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Opens an opportunity for the company (RF-011).
     *
     * <p>A lead starts with a status, an origin and a creation date, as the
     * acceptance criterion requires. The company has to belong to the active
     * organisation (otherwise 404) and must not be archived; there is one open
     * opportunity per company (doc 06 §3).
     */
    @Transactional
    public LeadEntity create(UUID companyId, LeadPriority priority, SourceData source) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        CompanyEntity company = companyService.requireActive(companyId);

        if (leadRepository.existsByOrganizationIdAndCompanyIdAndClosedAtIsNull(
                company.getOrganizationId(), company.getId())) {
            throw new ConflictException("Company already has an open lead");
        }

        Instant now = clock.instant();
        LeadEntity lead = leadRepository.save(new LeadEntity(
                UUID.randomUUID(), company, priority == null ? LeadPriority.MEDIUM : priority, now));

        recordStatusChange(lead, null, lead.getStatus(), null, user, now);
        companyService.recordLeadSource(company, lead.getId(), source);

        return lead;
    }

    /**
     * Opens a lead when the company has no open opportunity.
     *
     * <p>For the import: an existing company may already have an opportunity
     * in progress, possibly a blocked one. In that case nothing is created and
     * the import carries on, rather than failing as a whole.
     */
    @Transactional
    public Optional<LeadEntity> createIfNoneOpen(UUID companyId, LeadPriority priority, SourceData source) {
        UUID organizationId = organizationContext.requireOrganizationId();
        if (leadRepository.existsByOrganizationIdAndCompanyIdAndClosedAtIsNull(organizationId, companyId)) {
            return Optional.empty();
        }
        return Optional.of(create(companyId, priority, source));
    }

    @Transactional(readOnly = true)
    public LeadEntity find(UUID leadId) {
        return leadRepository
                .findScoped(leadId, organizationContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Lead"));
    }

    /**
     * A lead with its company, under an explicit organisation.
     *
     * <p>For internal use by jobs, which run without a session: the
     * organisation comes from the job, which was written from the session of
     * whoever requested it. Never call this with an organisation received from
     * the client (product rule 2).
     */
    @Transactional(readOnly = true)
    public Optional<LeadEntity> findInOrganization(UUID organizationId, UUID leadId) {
        return leadRepository.findScoped(leadId, organizationId);
    }

    /**
     * Applies the score of an analysis (D3). The same internal-use restriction
     * as {@link #findInOrganization}.
     */
    @Transactional
    public void applyScore(UUID organizationId, UUID leadId, int score, String version) {
        leadRepository.findScoped(leadId, organizationId)
                .ifPresent(lead -> lead.applyScore(score, version, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Page<LeadEntity> list(LeadSpecifications.Criteria criteria, Pageable pageable) {
        return leadRepository.findAll(
                LeadSpecifications.matching(organizationContext.requireOrganizationId(), criteria), pageable);
    }

    /**
     * Changes the status (doc 06 §2).
     *
     * <p>{@code CONTACTED} requires the operator to confirm explicitly that
     * the contact happened: generating or approving a message never moves the
     * lead, and the field exists so the interface cannot do it by accident.
     */
    @Transactional
    public LeadEntity changeStatus(UUID leadId, LeadStatus newStatus, String reason, boolean contactConfirmed) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        LeadEntity lead = find(leadId);

        if (newStatus == LeadStatus.CONTACTED && lead.getStatus() != LeadStatus.CONTACTED && !contactConfirmed) {
            throw new InvalidRequestException("Moving to CONTACTED requires the operator to confirm the contact");
        }
        if (!LeadStatusTransitions.isAllowed(lead.getStatus(), newStatus)) {
            throw new ConflictException("Status transition not allowed: " + lead.getStatus() + " to " + newStatus);
        }

        LeadStatus previous = lead.getStatus();
        Instant now = clock.instant();
        if (lead.changeStatus(newStatus, now)) {
            recordStatusChange(lead, previous, newStatus, reason, user, now);
            events.publishEvent(new LeadStatusChanged(
                    lead.getOrganizationId(), lead.getId(), previous, newStatus, Texts.blankToNull(reason)));
        }
        return lead;
    }

    /** The cap on a bulk action: a wide page of the queue, not the whole base. */
    public static final int MAX_BULK = 100;

    /**
     * The same change over several leads, in one transaction: either all of
     * them change or none does. An id from another organisation answers 404
     * and rolls the whole batch back, without saying which id it was. Owner
     * and priority only: changing status requires confirmation lead by lead
     * (rule 6, and the final states).
     *
     * @param assignee the owner when {@code priority} is null; null clears it
     */
    @Transactional
    public int bulkUpdate(List<UUID> leadIds, LeadPriority priority, UUID assignee) {
        List<UUID> distinct = leadIds.stream().distinct().toList();
        if (distinct.isEmpty() || distinct.size() > MAX_BULK) {
            throw new InvalidRequestException("leadIds must have between 1 and " + MAX_BULK + " items");
        }
        for (UUID leadId : distinct) {
            if (priority != null) {
                changePriority(leadId, priority);
            } else {
                assign(leadId, assignee);
            }
        }
        return distinct.size();
    }

    @Transactional
    public LeadEntity changePriority(UUID leadId, LeadPriority priority) {
        LeadEntity lead = find(leadId);
        lead.changePriority(priority, clock.instant());
        return lead;
    }

    /**
     * Sets the owner, or clears it with {@code null}.
     *
     * <p>The owner has to be an active member of the same organisation. An id
     * from another organisation answers as non-existent, without confirming
     * that it exists.
     */
    @Transactional
    public LeadEntity assign(UUID leadId, UUID assignedUserId) {
        LeadEntity lead = find(leadId);
        if (assignedUserId != null && !memberQueryService.isActiveMember(assignedUserId)) {
            throw new ResourceNotFoundException("Member");
        }
        lead.assignTo(assignedUserId, clock.instant());
        return lead;
    }

    @Transactional(readOnly = true)
    public Page<LeadTimelineQuery.Entry> timeline(UUID leadId, Pageable pageable) {
        LeadEntity lead = find(leadId);
        return timelineQuery.find(lead.getOrganizationId(), lead.getId(), pageable);
    }

    private void recordStatusChange(
            LeadEntity lead, LeadStatus from, LeadStatus to, String reason, AuthenticatedUser user, Instant now) {
        statusChangeRepository.save(new LeadStatusChangeEntity(
                UUID.randomUUID(),
                lead.getOrganizationId(),
                lead.getId(),
                from,
                to,
                Texts.blankToNull(reason),
                user.userId(),
                now));
    }
}
