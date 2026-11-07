package com.jhcomp.prospect.company.presentation;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.jhcomp.prospect.company.application.CompanyService.DuplicateCandidate;
import com.jhcomp.prospect.company.domain.DuplicateSignal;
import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceEntity;

/**
 * Responses of the company module.
 *
 * <p>They expose neither {@code organizationId} nor the normalised columns:
 * the first is implicit in the session, the others exist to compare and never
 * to display (doc 04 §2).
 */
public final class CompanyResponses {

    private CompanyResponses() {
    }

    public record CompanyResponse(
            UUID id,
            String name,
            String tradeName,
            String segment,
            String city,
            String state,
            String websiteUrl,
            String instagramUrl,
            String phone,
            String businessEmail,
            String address,
            String notes,
            boolean archived,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {

        public static CompanyResponse from(CompanyEntity company) {
            return new CompanyResponse(
                    company.getId(),
                    company.getName(),
                    company.getTradeName(),
                    company.getSegment(),
                    company.getCity(),
                    company.getState(),
                    company.getWebsiteUrl(),
                    company.getInstagramUrl(),
                    company.getPhone(),
                    company.getBusinessEmail(),
                    company.getAddress(),
                    company.getNotes(),
                    company.isArchived(),
                    company.getArchivedAt(),
                    company.getCreatedAt(),
                    company.getUpdatedAt());
        }
    }

    /** The origin of a value (RF-022). */
    public record SourceResponse(
            UUID id,
            UUID leadId,
            SourceType type,
            String name,
            String reference,
            String url,
            Instant collectedAt,
            UUID collectedByUserId,
            String note) {

        public static SourceResponse from(LeadSourceEntity source) {
            return new SourceResponse(
                    source.getId(),
                    source.getLeadId(),
                    source.getSourceType(),
                    source.getSourceName(),
                    source.getSourceReference(),
                    source.getSourceUrl(),
                    source.getCollectedAt(),
                    source.getCollectedByUserId(),
                    source.getNote());
        }
    }

    /** A possible duplicate (RF-014). A suggestion for review, never a merge. */
    public record DuplicateCandidateResponse(
            UUID companyId, String name, String city, String state, boolean archived, Set<DuplicateSignal> signals) {

        public static DuplicateCandidateResponse from(DuplicateCandidate candidate) {
            CompanyEntity company = candidate.company();
            return new DuplicateCandidateResponse(
                    company.getId(),
                    company.getName(),
                    company.getCity(),
                    company.getState(),
                    company.isArchived(),
                    Set.copyOf(candidate.signals()));
        }
    }
}
