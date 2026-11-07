package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Leads. Every query takes {@code organizationId} (RNF-001); filtered
 * listings use {@link LeadSpecifications}, which always applies the scope.
 */
public interface LeadRepository extends JpaRepository<LeadEntity, UUID>, JpaSpecificationExecutor<LeadEntity> {

    @Query("""
            select l from LeadEntity l
            join fetch l.company
            where l.id = :leadId
              and l.organizationId = :organizationId
            """)
    Optional<LeadEntity> findScoped(@Param("leadId") UUID leadId, @Param("organizationId") UUID organizationId);

    /** The company's open opportunity: the MVP allows one at a time (doc 06 §3). */
    boolean existsByOrganizationIdAndCompanyIdAndClosedAtIsNull(UUID organizationId, UUID companyId);

    /** Loads the company alongside: the listing shows name and city on every row. */
    @Override
    @EntityGraph(attributePaths = "company")
    Page<LeadEntity> findAll(Specification<LeadEntity> specification, Pageable pageable);
}
