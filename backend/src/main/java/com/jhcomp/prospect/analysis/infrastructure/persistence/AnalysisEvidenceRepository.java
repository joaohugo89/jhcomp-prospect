package com.jhcomp.prospect.analysis.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Evidencias. Toda consulta recebe {@code organizationId} (RNF-001). */
public interface AnalysisEvidenceRepository extends JpaRepository<AnalysisEvidenceEntity, UUID> {

    List<AnalysisEvidenceEntity> findByOrganizationIdAndFindingIdInOrderByObservedAtAscIdAsc(
            UUID organizationId, Collection<UUID> findingIds);
}
