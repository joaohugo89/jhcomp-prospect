package com.jhcomp.prospect.analysis.infrastructure.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jhcomp.prospect.analysis.domain.AnalysisStatus;

/** Analises. Toda consulta recebe {@code organizationId} (RNF-001). */
public interface LeadAnalysisRepository extends JpaRepository<LeadAnalysisEntity, UUID> {

    Optional<LeadAnalysisEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<LeadAnalysisEntity> findByOrganizationIdAndLeadIdOrderByCreatedAtDescIdAsc(
            UUID organizationId, UUID leadId, Pageable pageable);

    Optional<LeadAnalysisEntity> findFirstByOrganizationIdAndLeadIdAndStatusIn(
            UUID organizationId, UUID leadId, Collection<AnalysisStatus> statuses);

    /** Ultima analise concluida: e ela que define o score do lead (D3). */
    Optional<LeadAnalysisEntity> findFirstByOrganizationIdAndLeadIdAndStatusOrderByCompletedAtDescIdAsc(
            UUID organizationId, UUID leadId, AnalysisStatus status);
}
