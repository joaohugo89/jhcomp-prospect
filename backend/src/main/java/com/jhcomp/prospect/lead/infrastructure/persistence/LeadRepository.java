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
 * Leads. Toda consulta recebe {@code organizationId} (RNF-001); listagens
 * filtradas usam {@link LeadSpecifications}, que sempre aplica o escopo.
 */
public interface LeadRepository extends JpaRepository<LeadEntity, UUID>, JpaSpecificationExecutor<LeadEntity> {

    @Query("""
            select l from LeadEntity l
            join fetch l.company
            where l.id = :leadId
              and l.organizationId = :organizationId
            """)
    Optional<LeadEntity> findScoped(@Param("leadId") UUID leadId, @Param("organizationId") UUID organizationId);

    /** Oportunidade aberta da empresa: o MVP admite uma por vez (doc 06 §3). */
    boolean existsByOrganizationIdAndCompanyIdAndClosedAtIsNull(UUID organizationId, UUID companyId);

    /** Carrega a empresa junto: a listagem exibe nome e cidade em toda linha. */
    @Override
    @EntityGraph(attributePaths = "company")
    Page<LeadEntity> findAll(Specification<LeadEntity> specification, Pageable pageable);
}
