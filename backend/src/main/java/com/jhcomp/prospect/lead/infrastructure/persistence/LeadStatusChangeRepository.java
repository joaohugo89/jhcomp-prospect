package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Historico de status. Leitura pela {@link LeadTimelineQuery}, sempre escopada. */
public interface LeadStatusChangeRepository extends JpaRepository<LeadStatusChangeEntity, UUID> {

    /**
     * Ultima mudanca de cada lead, que o levou ao status atual (toda mudanca
     * grava uma linha, inclusive a abertura). Uma consulta para o quadro todo.
     *
     * <p>"Ultima" e pela ordem de gravacao ({@code recorded_seq}, a mesma da
     * timeline), nao pelo maior {@code changed_at}: relogios de maquinas
     * diferentes nao sao confiaveis para ordenar eventos.
     */
    @Query(value = """
            select distinct on (lead_id) *
              from lead_status_change
             where organization_id = :organizationId and lead_id in (:leadIds)
             order by lead_id, recorded_seq desc
            """, nativeQuery = true)
    List<LeadStatusChangeEntity> findLatestByLead(
            @Param("organizationId") UUID organizationId, @Param("leadIds") Collection<UUID> leadIds);
}
