package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Visoes salvas, sempre filtradas por organizacao; privadas tambem pelo dono. */
public interface LeadViewRepository extends JpaRepository<LeadViewEntity, UUID> {

    /** As proprias privadas e todas as compartilhadas da organizacao; compartilhadas primeiro. */
    @Query("""
            select v from LeadViewEntity v
             where v.organizationId = :organizationId and (v.shared = true or v.userId = :userId)
             order by v.shared desc, lower(v.name)
            """)
    List<LeadViewEntity> findVisible(@Param("organizationId") UUID organizationId, @Param("userId") UUID userId);

    long countByOrganizationIdAndUserIdAndSharedFalse(UUID organizationId, UUID userId);

    long countByOrganizationIdAndSharedTrue(UUID organizationId);

    boolean existsByOrganizationIdAndUserIdAndSharedFalseAndNameIgnoreCase(UUID organizationId, UUID userId, String name);

    boolean existsByOrganizationIdAndSharedTrueAndNameIgnoreCase(UUID organizationId, String name);

    Optional<LeadViewEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
