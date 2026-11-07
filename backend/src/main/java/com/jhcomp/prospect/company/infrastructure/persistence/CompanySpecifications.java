package com.jhcomp.prospect.company.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.jhcomp.prospect.shared.normalization.ContactNormalizer;
import com.jhcomp.prospect.shared.persistence.LikePatterns;

import jakarta.persistence.criteria.Predicate;

/**
 * Combinable filters for the company listing (RF-012).
 *
 * <p>The organisation scope is not an optional filter: it is always applied,
 * and there is no way to build the query without it.
 */
public final class CompanySpecifications {

    private CompanySpecifications() {
    }

    /** Listing criteria. Null or empty fields filter nothing. */
    public record Criteria(String search, String segment, String city, String state, boolean includeArchived) {}

    public static Specification<CompanyEntity> matching(UUID organizationId, Criteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("organizationId"), organizationId));

            if (!criteria.includeArchived()) {
                predicates.add(builder.isNull(root.get("archivedAt")));
            }

            // The search compares against the normalised name, by the same
            // rule used when writing (D10): "salao" finds "Salão", and extra
            // whitespace in the term does not prevent a match.
            ContactNormalizer.name(criteria.search()).ifPresent(term -> predicates.add(builder.like(
                    root.get("normalizedName"), LikePatterns.contains(term), LikePatterns.ESCAPE)));

            if (hasText(criteria.segment())) {
                // The same segment in another case or with surrounding spaces (CompanyOriginSql.SEGMENT_EQUALS).
                predicates.add(builder.equal(
                        builder.lower(builder.trim(root.get("segment"))),
                        criteria.segment().strip().toLowerCase(Locale.ROOT)));
            }
            if (hasText(criteria.city())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("city")), criteria.city().strip().toLowerCase(Locale.ROOT)));
            }
            if (hasText(criteria.state())) {
                predicates.add(builder.equal(root.get("state"), criteria.state().strip().toUpperCase(Locale.ROOT)));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
