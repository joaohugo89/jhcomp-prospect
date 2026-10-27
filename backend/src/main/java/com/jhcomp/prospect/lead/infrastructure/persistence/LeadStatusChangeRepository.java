package com.jhcomp.prospect.lead.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Historico de status. Leitura pela {@link LeadTimelineQuery}, sempre escopada. */
public interface LeadStatusChangeRepository extends JpaRepository<LeadStatusChangeEntity, UUID> {
}
