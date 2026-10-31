package com.jhcomp.prospect.crm;

import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Tarefa ligada a um lead (RF-042). */
@Entity
@Table(name = "task")
public class TaskEntity extends AssignedIdEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Column(nullable = false)
    private String title;

    @Column(name = "due_at")
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TaskEntity() {
        // Exigido pelo JPA.
    }

    TaskEntity(
            UUID organizationId, UUID leadId, String title, Instant dueAt, UUID assignedUserId, UUID createdBy, Instant now) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.leadId = leadId;
        this.title = title;
        this.dueAt = dueAt;
        this.status = TaskStatus.OPEN;
        this.assignedUserId = assignedUserId;
        this.createdByUserId = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String newTitle, Instant newDueAt, UUID newAssignee, TaskStatus newStatus, Instant now) {
        this.title = newTitle;
        this.dueAt = newDueAt;
        this.assignedUserId = newAssignee;
        if (newStatus != status) {
            this.status = newStatus;
            this.completedAt = newStatus == TaskStatus.DONE ? now : null;
        }
        this.updatedAt = now;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
