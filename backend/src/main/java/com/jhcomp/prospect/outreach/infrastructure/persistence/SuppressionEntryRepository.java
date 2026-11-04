package com.jhcomp.prospect.outreach.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jhcomp.prospect.outreach.domain.IdentifierType;

public interface SuppressionEntryRepository extends JpaRepository<SuppressionEntryEntity, UUID> {

    Page<SuppressionEntryEntity> findByOrganizationIdOrderByCreatedAtDescIdAsc(UUID organizationId, Pageable pageable);

    Page<SuppressionEntryEntity> findByOrganizationIdAndIdentifierTypeInOrderByCreatedAtDescIdAsc(
            UUID organizationId, Collection<IdentifierType> types, Pageable pageable);

    Page<SuppressionEntryEntity> findByOrganizationIdAndIdentifierTypeInAndIdentifierInOrderByCreatedAtDescIdAsc(
            UUID organizationId, Collection<IdentifierType> types, Collection<String> identifiers, Pageable pageable);

    long countByOrganizationIdAndIdentifierType(UUID organizationId, IdentifierType type);

    /**
     * Entradas com algum dos valores, de qualquer tipo e canal. O chamador
     * filtra tipo e canal: sao poucas linhas por empresa.
     */
    List<SuppressionEntryEntity> findByOrganizationIdAndIdentifierIn(UUID organizationId, Collection<String> identifiers);
}
