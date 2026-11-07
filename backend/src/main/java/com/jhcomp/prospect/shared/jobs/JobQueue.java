package com.jhcomp.prospect.shared.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The persistent job queue (D15).
 *
 * <p>Polling a table, with no broker (docs/03 §7). Claiming uses
 * {@code FOR UPDATE SKIP LOCKED}: several instances can consume the same queue
 * without picking up the same job.
 */
@Component
public class JobQueue {

    /** A job running for longer than this belongs to a worker that died. */
    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private static final String CLAIM = """
            update job
               set status = 'RUNNING', locked_at = :now, updated_at = :now, attempts = attempts + 1
             where id = (
                    select id from job
                     where (status = 'PENDING' and run_after <= :now)
                        or (status = 'RUNNING' and locked_at < :staleBefore and attempts < max_attempts)
                     order by run_after
                     limit 1
                       for update skip locked)
            returning id
            """;

    private final JobRepository repository;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    JobQueue(JobRepository repository, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Enqueues inside the caller's transaction: the job only exists if the
     * record that motivated it is written too.
     *
     * @param idempotencyKey prevents a second identical job while the first is
     *     still active (a partial unique index)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(UUID organizationId, String jobType, String payload, int maxAttempts, String idempotencyKey) {
        return repository.save(new JobEntity(
                        organizationId, jobType, payload, maxAttempts, idempotencyKey, clock.instant()))
                .getId();
    }

    /** Claims the next ready job, or none. A short transaction, the claim only. */
    @Transactional
    public Optional<JobEntity> claimNext() {
        Instant now = clock.instant();
        List<UUID> claimed = jdbc.queryForList(
                CLAIM,
                new MapSqlParameterSource()
                        .addValue("now", java.sql.Timestamp.from(now))
                        .addValue("staleBefore", java.sql.Timestamp.from(now.minus(STALE_AFTER))),
                UUID.class);
        return claimed.stream().findFirst().flatMap(repository::findById);
    }

    @Transactional
    public void markSucceeded(UUID jobId) {
        repository.findById(jobId).ifPresent(job -> job.succeed(clock.instant()));
    }

    /** @return the job, when there will be no further attempt */
    @Transactional
    public Optional<JobEntity> markFailed(UUID jobId, String error) {
        return repository.findById(jobId).filter(job -> job.fail(error, clock.instant()));
    }
}
