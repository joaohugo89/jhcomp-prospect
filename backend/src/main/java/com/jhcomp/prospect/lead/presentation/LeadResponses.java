package com.jhcomp.prospect.lead.presentation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.lead.domain.LeadStatusTransitions;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadTimelineQuery;

/** Responses of the lead module. They do not expose {@code organizationId}. */
public final class LeadResponses {

    private LeadResponses() {
    }

    /** A company summary, enough to list it and navigate to it. */
    public record CompanySummary(UUID id, String name, String segment, String city, String state, boolean archived) {

        static CompanySummary from(CompanyEntity company) {
            return new CompanySummary(
                    company.getId(),
                    company.getName(),
                    company.getSegment(),
                    company.getCity(),
                    company.getState(),
                    company.isArchived());
        }
    }

    /**
     * A lead.
     *
     * <p>A null {@code score} means "no analysis", never zero (D7).
     * {@code allowedTransitions} guides the interface; the rule itself stays
     * on the server.
     */
    public record LeadResponse(
            UUID id,
            CompanySummary company,
            LeadStatus status,
            LeadPriority priority,
            Integer score,
            String scoreVersion,
            UUID assignedUserId,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt,
            List<LeadStatus> allowedTransitions) {

        public static LeadResponse from(LeadEntity lead) {
            return new LeadResponse(
                    lead.getId(),
                    CompanySummary.from(lead.getCompany()),
                    lead.getStatus(),
                    lead.getPriority(),
                    lead.getScore(),
                    lead.getScoreVersion(),
                    lead.getAssignedUserId(),
                    lead.getCreatedAt(),
                    lead.getUpdatedAt(),
                    lead.getClosedAt(),
                    LeadStatusTransitions.allowedFrom(lead.getStatus()).stream()
                            .sorted(Comparator.naturalOrder())
                            .toList());
        }
    }

    public record TimelineEntryResponse(
            LeadTimelineQuery.EntryType type,
            UUID id,
            Instant occurredAt,
            String actorName,
            String fromStatus,
            String toStatus,
            String reason,
            String sourceType,
            String sourceName,
            String sourceUrl,
            String sourceReference,
            String interactionType,
            String channel,
            String direction,
            String outcome,
            String notes) {

        public static TimelineEntryResponse from(LeadTimelineQuery.Entry entry) {
            return new TimelineEntryResponse(
                    entry.type(),
                    entry.id(),
                    entry.occurredAt(),
                    entry.actorName(),
                    entry.fromStatus(),
                    entry.toStatus(),
                    entry.reason(),
                    entry.sourceType(),
                    entry.sourceName(),
                    entry.sourceUrl(),
                    entry.sourceReference(),
                    entry.interactionType(),
                    entry.channel(),
                    entry.direction(),
                    entry.outcome(),
                    entry.notes());
        }
    }
}
