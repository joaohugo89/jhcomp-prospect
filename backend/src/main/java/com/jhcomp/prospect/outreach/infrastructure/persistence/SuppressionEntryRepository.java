package com.jhcomp.prospect.outreach.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuppressionEntryRepository extends JpaRepository<SuppressionEntryEntity, UUID> {

    Page<SuppressionEntryEntity> findByOrganizationIdOrderByCreatedAtDescIdAsc(UUID organizationId, Pageable pageable);

    /**
     * Entradas com algum dos valores, de qualquer tipo e canal. O chamador
     * filtra tipo e canal: sao poucas linhas por empresa.
     */
    List<SuppressionEntryEntity> findByOrganizationIdAndIdentifierIn(UUID organizationId, Collection<String> identifiers);
}
