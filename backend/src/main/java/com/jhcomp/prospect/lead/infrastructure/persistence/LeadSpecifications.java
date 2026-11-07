package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.jhcomp.prospect.company.domain.SourceType;
import com.jhcomp.prospect.company.infrastructure.persistence.CompanyEntity;
import com.jhcomp.prospect.company.infrastructure.persistence.LeadSourceEntity;
import com.jhcomp.prospect.lead.domain.LeadPriority;
import com.jhcomp.prospect.lead.domain.LeadStatus;
import com.jhcomp.prospect.shared.normalization.ContactNormalizer;
import com.jhcomp.prospect.shared.persistence.LikePatterns;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

/**
 * Combinable filters for the lead listing (RF-012, doc 05 §5).
 *
 * <p>The organisation scope is always applied, including inside the origin
 * subquery.
 */
public final class LeadSpecifications {

    private LeadSpecifications() {
    }

    /**
     * Listing criteria. Null fields filter nothing. {@code closedSince} keeps
     * only the leads closed from that instant onwards (the board's final
     * columns); an open lead has no closing date and stays out.
     */
    public record Criteria(
            LeadStatus status,
            LeadPriority priority,
            String segment,
            String city,
            String search,
            Integer scoreMin,
            Integer scoreMax,
            SourceType source,
            Instant closedSince) {}

    public static Specification<LeadEntity> matching(UUID organizationId, Criteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), organizationId));

            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.closedSince() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("closedAt"), criteria.closedSince()));
            }
            if (criteria.priority() != null) {
                predicates.add(builder.equal(root.get("priority"), criteria.priority()));
            }
            // A range filter excludes leads with no score: no analysis is not
            // the same as a low score (doc 06 §7, D7).
            if (criteria.scoreMin() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("score"), criteria.scoreMin()));
            }
            if (criteria.scoreMax() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("score"), criteria.scoreMax()));
            }

            boolean filtersCompany = hasText(criteria.segment()) || hasText(criteria.city()) || hasText(criteria.search());
            if (filtersCompany) {
                Join<LeadEntity, CompanyEntity> company = root.join("company");
                if (hasText(criteria.segment())) {
                    predicates.add(builder.equal(
                            builder.lower(builder.trim(company.get("segment"))),
                            criteria.segment().strip().toLowerCase(Locale.ROOT)));
                }
                if (hasText(criteria.city())) {
                    predicates.add(builder.equal(
                            builder.lower(company.get("city")), criteria.city().strip().toLowerCase(Locale.ROOT)));
                }
                ContactNormalizer.name(criteria.search()).ifPresent(term -> predicates.add(builder.like(
                        company.get("normalizedName"), LikePatterns.contains(term), LikePatterns.ESCAPE)));
            }

            if (criteria.source() != null) {
                Subquery<UUID> sources = query.subquery(UUID.class);
                var source = sources.from(LeadSourceEntity.class);
                sources.select(source.get("id")).where(
                        builder.equal(source.get("organizationId"), organizationId),
                        builder.equal(source.get("leadId"), root.get("id")),
                        builder.equal(source.get("sourceType"), criteria.source()));
                predicates.add(builder.exists(sources));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
