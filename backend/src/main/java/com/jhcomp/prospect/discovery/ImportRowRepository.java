package com.jhcomp.prospect.discovery;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Linhas de importacao. Toda consulta recebe {@code organizationId} (RNF-001). */
interface ImportRowRepository extends JpaRepository<ImportRowEntity, UUID> {

    List<ImportRowEntity> findByOrganizationIdAndImportIdOrderByRowNumber(UUID organizationId, UUID importId);

    Page<ImportRowEntity> findByOrganizationIdAndImportIdOrderByRowNumber(
            UUID organizationId, UUID importId, Pageable pageable);

    Page<ImportRowEntity> findByOrganizationIdAndImportIdAndStatusOrderByRowNumber(
            UUID organizationId, UUID importId, ImportRowEntity.Status status, Pageable pageable);
}
