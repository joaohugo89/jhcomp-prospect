package com.jhcomp.prospect.outreach.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutreachMessageRepository extends JpaRepository<OutreachMessageEntity, UUID> {

    Optional<OutreachMessageEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<OutreachMessageEntity> findByOrganizationIdAndLeadIdOrderByCreatedAtDescIdAsc(
            UUID organizationId, UUID leadId, Pageable pageable);
}
