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

/** Respostas do modulo de lead. Nao expoem {@code organizationId}. */
public final class LeadResponses {

    private LeadResponses() {
    }

    /** Resumo da empresa, suficiente para listar e navegar ate ela. */
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
     * Lead.
     *
     * <p>{@code score} nulo significa "sem analise", nunca zero (D7).
     * {@code allowedTransitions} orienta a interface; a regra continua no
     * servidor.
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
            String sourceReference) {

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
                    entry.sourceReference());
        }
    }
}
