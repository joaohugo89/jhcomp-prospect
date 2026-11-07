package com.jhcomp.prospect.outreach.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.jhcomp.prospect.outreach.domain.IdentifierType;

public interface SuppressionEntryRepository extends JpaRepository<SuppressionEntryEntity, UUID> {

    Page<SuppressionEntryEntity> findByOrganizationIdOrderByCreatedAtDescIdAsc(UUID organizationId, Pageable pageable);

    Page<SuppressionEntryEntity> findByOrganizationIdAndIdentifierTypeInOrderByCreatedAtDescIdAsc(
            UUID organizationId, Collection<IdentifierType> types, Pageable pageable);

    Page<SuppressionEntryEntity> findByOrganizationIdAndIdentifierTypeInAndIdentifierInOrderByCreatedAtDescIdAsc(
            UUID organizationId, Collection<IdentifierType> types, Collection<String> identifiers, Pageable pageable);

    /**
     * Entry count per identifier type, in a single round trip.
     *
     * <p>Grouping in SQL rather than counting once per enum constant: the
     * suppression screen header needs every type, and one query per type
     * grows with the enum while returning the same handful of numbers.
     */
    @Query("select e.identifierType as identifierType, count(e) as total from SuppressionEntryEntity e "
            + "where e.organizationId = :organizationId group by e.identifierType")
    List<IdentifierTypeCount> countGroupedByIdentifierType(UUID organizationId);

    /**
     * Projection for {@link #countGroupedByIdentifierType(UUID)}. The aliases
     * in the query must match these accessors.
     */
    interface IdentifierTypeCount {
        IdentifierType getIdentifierType();

        long getTotal();
    }

    /**
     * Entries holding any of the values, of any type and channel. The caller
     * filters type and channel: there are few rows per company.
     */
    List<SuppressionEntryEntity> findByOrganizationIdAndIdentifierIn(UUID organizationId, Collection<String> identifiers);
}
