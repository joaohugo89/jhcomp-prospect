package com.jhcomp.prospect.company.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Proveniencia. Toda consulta recebe {@code organizationId} (RNF-001). */
public interface LeadSourceRepository extends JpaRepository<LeadSourceEntity, UUID> {

    Page<LeadSourceEntity> findByOrganizationIdAndCompanyIdOrderByCollectedAtDescIdAsc(
            UUID organizationId, UUID companyId, Pageable pageable);
}
