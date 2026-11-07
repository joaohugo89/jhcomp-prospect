package com.jhcomp.prospect.discovery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Imports. Every query takes {@code organizationId} (RNF-001). */
interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, UUID> {

    Optional<ImportBatchEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<ImportBatchEntity> findByOrganizationIdOrderByCreatedAtDescIdAsc(UUID organizationId, Pageable pageable);

    Page<ImportBatchEntity> findByOrganizationIdAndStatusOrderByCreatedAtDescIdAsc(
            UUID organizationId, ImportBatchEntity.Status status, Pageable pageable);

    /** The history overview (doc 08 §9): one query, counts per status. */
    @Query("""
            select count(case when b.status = com.jhcomp.prospect.discovery.ImportBatchEntity.Status.ANALYZED
                              then 1 end),
                   count(case when b.status = com.jhcomp.prospect.discovery.ImportBatchEntity.Status.COMPLETED
                              and b.undoneAt is null then 1 end),
                   count(case when b.undoneAt is not null then 1 end),
                   count(case when b.status = com.jhcomp.prospect.discovery.ImportBatchEntity.Status.DISCARDED
                              then 1 end),
                   coalesce(sum(case when b.status = com.jhcomp.prospect.discovery.ImportBatchEntity.Status.COMPLETED
                                     and b.undoneAt is null then b.importedRows else 0 end), 0)
              from ImportBatchEntity b
             where b.organizationId = :organizationId
            """)
    List<Object[]> summarize(@Param("organizationId") UUID organizationId);

    /** An earlier import of the same file, to flag a resend (doc 10 §4). */
    Optional<ImportBatchEntity> findFirstByOrganizationIdAndFileSha256AndIdNotAndCreatedAtBeforeAndStatusNotOrderByCreatedAtDesc(
            UUID organizationId,
            String fileSha256,
            UUID excludedId,
            Instant createdBefore,
            ImportBatchEntity.Status excludedStatus);

    /**
     * Moves from {@code from} to {@code to} only while it is still in
     * {@code from}.
     *
     * <p>Atomic: two simultaneous confirmations cannot import the same file
     * twice, because only one of them wins the transition.
     *
     * @return 1 when the transition happened, 0 otherwise
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ImportBatchEntity b set b.status = :to
            where b.id = :id and b.organizationId = :organizationId and b.status = :from
            """)
    int transition(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("from") ImportBatchEntity.Status from,
            @Param("to") ImportBatchEntity.Status to);
}
