package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Visoes salvas, sempre filtradas por organizacao e dono. */
public interface LeadViewRepository extends JpaRepository<LeadViewEntity, UUID> {

    List<LeadViewEntity> findByOrganizationIdAndUserIdOrderByNameAsc(UUID organizationId, UUID userId);

    long countByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    boolean existsByOrganizationIdAndUserIdAndNameIgnoreCase(UUID organizationId, UUID userId, String name);

    Optional<LeadViewEntity> findByIdAndOrganizationIdAndUserId(UUID id, UUID organizationId, UUID userId);
}
