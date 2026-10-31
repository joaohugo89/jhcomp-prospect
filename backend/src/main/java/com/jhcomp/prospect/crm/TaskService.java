package com.jhcomp.prospect.crm;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.application.MemberQueryService;
import com.jhcomp.prospect.lead.application.LeadService;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadEntity;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.OrganizationContext;

/** Tarefas do lead (RF-042). */
@Service
public class TaskService {

    private final TaskRepository repository;
    private final LeadService leadService;
    private final MemberQueryService memberQueryService;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    TaskService(
            TaskRepository repository,
            LeadService leadService,
            MemberQueryService memberQueryService,
            OrganizationContext organizationContext,
            Clock clock) {
        this.repository = repository;
        this.leadService = leadService;
        this.memberQueryService = memberQueryService;
        this.organizationContext = organizationContext;
        this.clock = clock;
    }

    @Transactional
    public TaskEntity create(UUID leadId, String title, Instant dueAt, UUID assignedUserId) {
        AuthenticatedUser user = organizationContext.requireCurrentUser();
        LeadEntity lead = leadService.find(leadId);
        requireMember(assignedUserId);
        return repository.save(new TaskEntity(
                lead.getOrganizationId(), lead.getId(), title.strip(), dueAt, assignedUserId, user.userId(),
                clock.instant()));
    }

    /** Substitui titulo, prazo, responsavel e situacao: o cliente envia o estado completo. */
    @Transactional
    public TaskEntity update(UUID taskId, String title, Instant dueAt, UUID assignedUserId, TaskStatus status) {
        TaskEntity task = repository.findByIdAndOrganizationId(taskId, organizationContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Task"));
        requireMember(assignedUserId);
        task.update(title.strip(), dueAt, assignedUserId, status, clock.instant());
        return task;
    }

    @Transactional(readOnly = true)
    public Page<TaskEntity> list(UUID leadId, Pageable pageable) {
        LeadEntity lead = leadService.find(leadId);
        return repository.findByLead(lead.getOrganizationId(), lead.getId(), pageable);
    }

    /** Responsavel de outra organizacao responde como inexistente, sem confirmar que existe. */
    private void requireMember(UUID userId) {
        if (userId != null && !memberQueryService.isActiveMember(userId)) {
            throw new ResourceNotFoundException("Member");
        }
    }
}
