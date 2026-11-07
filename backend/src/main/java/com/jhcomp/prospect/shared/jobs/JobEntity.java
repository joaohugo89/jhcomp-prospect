package com.jhcomp.prospect.shared.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Background work, persisted so it survives a restart (D15). */
@Entity
@Table(name = "job")
public class JobEntity extends AssignedIdEntity {

    public enum Status {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    /** The error summary kept on the job: short, and free of internal detail. */
    private static final int MAX_ERROR_LENGTH = 500;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "job_type", nullable = false, updatable = false)
    private String jobType;

    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

    @Column(name = "run_after", nullable = false)
    private Instant runAfter;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected JobEntity() {
        // Required by JPA.
    }

    JobEntity(
            UUID organizationId,
            String jobType,
            String payload,
            int maxAttempts,
            String idempotencyKey,
            Instant now) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.jobType = jobType;
        this.payload = payload;
        this.status = Status.PENDING;
        this.maxAttempts = maxAttempts;
        this.idempotencyKey = idempotencyKey;
        this.runAfter = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void succeed(Instant now) {
        this.status = Status.SUCCEEDED;
        this.lockedAt = null;
        this.lastError = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    /**
     * Records the failure: it returns to the queue with increasing backoff
     * or, once the attempts are exhausted, fails for good.
     *
     * @return {@code true} when there will be no further attempt
     */
    boolean fail(String error, Instant now) {
        this.lastError = error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        this.lockedAt = null;
        this.updatedAt = now;
        if (attempts >= maxAttempts) {
            this.status = Status.FAILED;
            this.completedAt = now;
            return true;
        }
        this.status = Status.PENDING;
        // 30 s, 2 min, 8 min…: a transient network failure has time to pass.
        this.runAfter = now.plus(Duration.ofSeconds(30L << (2 * (attempts - 1))));
        return false;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getJobType() {
        return jobType;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }
}
