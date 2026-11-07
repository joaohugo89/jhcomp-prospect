package com.jhcomp.prospect.company.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Companies.
 *
 * <p>Every query takes {@code organizationId} (RNF-001, doc 04 §4). The
 * inherited {@code findById} is not used by the use cases: looking something
 * up by id without a scope is exactly what allows cross-organisation access.
 * Filtered listings use {@link CompanySpecifications}, which always applies
 * the scope.
 */
public interface CompanyRepository
        extends JpaRepository<CompanyEntity, UUID>, JpaSpecificationExecutor<CompanyEntity> {

    Optional<CompanyEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * The spelling already used in the organisation for the same segment,
     * ignoring case: the most used one (D31). Empty when the segment is
     * new.
     */
    @Query(value = """
            select segment from company
             where organization_id = :organizationId and lower(segment) = lower(:segment)
             group by segment
             order by count(*) desc, segment
             limit 1
            """, nativeQuery = true)
    Optional<String> findExistingSegment(
            @Param("organizationId") UUID organizationId, @Param("segment") String segment);

    // Duplicate candidates (doc 06 §4). One method per signal, so each query
    // uses the matching index from V3.

    List<CompanyEntity> findTop10ByOrganizationIdAndNormalizedDomainAndIdNot(
            UUID organizationId, String normalizedDomain, UUID excludedId);

    List<CompanyEntity> findTop10ByOrganizationIdAndNormalizedPhoneAndIdNot(
            UUID organizationId, String normalizedPhone, UUID excludedId);

    List<CompanyEntity> findTop10ByOrganizationIdAndNormalizedNameAndCityIgnoreCaseAndStateAndIdNot(
            UUID organizationId, String normalizedName, String city, String state, UUID excludedId);

    // Batch variants, for the import: one query per signal for the whole
    // file, instead of three per row.

    List<CompanyEntity> findByOrganizationIdAndNormalizedDomainIn(UUID organizationId, Collection<String> domains);

    List<CompanyEntity> findByOrganizationIdAndNormalizedPhoneIn(UUID organizationId, Collection<String> phones);

    List<CompanyEntity> findByOrganizationIdAndNormalizedNameIn(UUID organizationId, Collection<String> names);
}
