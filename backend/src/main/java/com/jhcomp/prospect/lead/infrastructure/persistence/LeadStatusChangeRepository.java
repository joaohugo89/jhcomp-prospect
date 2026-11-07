package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** The status history. Read through {@link LeadTimelineQuery}, always scoped. */
public interface LeadStatusChangeRepository extends JpaRepository<LeadStatusChangeEntity, UUID> {

    /**
     * Each lead's last change, the one that brought it to its current status
     * (every change writes a row, the opening included). One query for the
     * whole board.
     *
     * <p>"Last" means by recording order ({@code recorded_seq}, the same as
     * the timeline), not by the greatest {@code changed_at}: clocks on
     * different machines are not reliable for ordering events.
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
