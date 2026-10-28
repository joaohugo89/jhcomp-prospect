package com.jhcomp.prospect.discovery;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Importacoes. Toda consulta recebe {@code organizationId} (RNF-001). */
interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, UUID> {

    Optional<ImportBatchEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<ImportBatchEntity> findByOrganizationIdOrderByCreatedAtDescIdAsc(UUID organizationId, Pageable pageable);

    /** Importacao anterior do mesmo arquivo, para avisar reenvio (doc 10 §4). */
    Optional<ImportBatchEntity> findFirstByOrganizationIdAndFileSha256AndIdNotAndStatusNotOrderByCreatedAtDesc(
            UUID organizationId, String fileSha256, UUID excludedId, ImportBatchEntity.Status excludedStatus);

    /**
     * Passa de {@code from} para {@code to} so se ainda estiver em {@code from}.
     *
     * <p>Atomico: duas confirmacoes simultaneas nao importam o mesmo arquivo
     * duas vezes, porque so uma consegue a transicao.
     *
     * @return 1 se a transicao ocorreu, 0 caso contrario
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
