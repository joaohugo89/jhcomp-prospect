package com.jhcomp.prospect.crm;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Interacoes. Toda consulta recebe {@code organizationId} (RNF-001). */
interface InteractionRepository extends JpaRepository<InteractionEntity, UUID> {

    Page<InteractionEntity> findByOrganizationIdAndLeadIdOrderByOccurredAtDescIdAsc(
            UUID organizationId, UUID leadId, Pageable pageable);

    /** Ultima interacao de cada lead, para os cards do Kanban: uma consulta para todos. */
    @Query(value = """
            select distinct on (lead_id) *
              from interaction
             where organization_id = :organizationId and lead_id in (:leadIds)
             order by lead_id, occurred_at desc, recorded_seq desc
            """, nativeQuery = true)
    List<InteractionEntity> findLatestByLead(
            @Param("organizationId") UUID organizationId, @Param("leadIds") Collection<UUID> leadIds);
}
