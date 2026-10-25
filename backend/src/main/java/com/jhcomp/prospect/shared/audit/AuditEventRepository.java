package com.jhcomp.prospect.shared.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    List<AuditEventEntity> findByActionOrderByOccurredAtDesc(AuditAction action);

    List<AuditEventEntity> findByOrganizationIdOrderByOccurredAtDesc(UUID organizationId);
}
