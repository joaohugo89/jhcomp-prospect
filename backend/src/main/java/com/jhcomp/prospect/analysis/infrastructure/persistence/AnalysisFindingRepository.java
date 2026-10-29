package com.jhcomp.prospect.analysis.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Achados. Toda consulta recebe {@code organizationId} (RNF-001). */
public interface AnalysisFindingRepository extends JpaRepository<AnalysisFindingEntity, UUID> {

    Optional<AnalysisFindingEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<AnalysisFindingEntity> findByOrganizationIdAndAnalysisId(UUID organizationId, UUID analysisId);
}
