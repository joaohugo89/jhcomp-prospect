package com.jhcomp.prospect.crm;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Deals. Toda consulta recebe {@code organizationId} (RNF-001). */
interface DealRepository extends JpaRepository<DealEntity, UUID> {

    Optional<DealEntity> findByOrganizationIdAndLeadId(UUID organizationId, UUID leadId);

    List<DealEntity> findByOrganizationIdAndLeadIdIn(UUID organizationId, Collection<UUID> leadIds);
}
