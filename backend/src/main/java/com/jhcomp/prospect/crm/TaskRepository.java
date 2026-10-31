package com.jhcomp.prospect.crm;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tarefas. Toda consulta recebe {@code organizationId} (RNF-001). */
interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    Optional<TaskEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** Abertas primeiro, pelo prazo mais proximo; sem prazo por ultimo. */
    @Query("""
            select t from TaskEntity t
             where t.organizationId = :organizationId and t.leadId = :leadId
             order by case when t.status = com.jhcomp.prospect.crm.TaskStatus.OPEN then 0 else 1 end,
                      t.dueAt asc nulls last, t.createdAt asc
            """)
    Page<TaskEntity> findByLead(
            @Param("organizationId") UUID organizationId, @Param("leadId") UUID leadId, Pageable pageable);

    /** Proxima tarefa aberta de cada lead, para os cards do Kanban. */
    @Query(value = """
            select distinct on (lead_id) *
              from task
             where organization_id = :organizationId and status = 'OPEN' and lead_id in (:leadIds)
             order by lead_id, due_at asc nulls last, created_at asc
            """, nativeQuery = true)
    List<TaskEntity> findNextOpenByLead(
            @Param("organizationId") UUID organizationId, @Param("leadIds") Collection<UUID> leadIds);
}
