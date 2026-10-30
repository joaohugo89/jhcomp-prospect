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
 * Fila persistente de jobs (D15).
 *
 * <p>Polling em tabela, sem broker (docs/03 §7). A tomada usa
 * {@code FOR UPDATE SKIP LOCKED}: varias instancias podem consumir a mesma
 * fila sem pegar o mesmo job.
 */
@Component
public class JobQueue {

    /** Job em execucao ha mais que isso e de um worker que caiu. */
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
     * Enfileira na transacao de quem chama: o job so existe se o registro que
     * o motivou tambem for gravado.
     *
     * @param idempotencyKey impede um segundo job igual enquanto o primeiro
     *     estiver ativo (indice unico parcial)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(UUID organizationId, String jobType, String payload, int maxAttempts, String idempotencyKey) {
        return repository.save(new JobEntity(
                        organizationId, jobType, payload, maxAttempts, idempotencyKey, clock.instant()))
                .getId();
    }

    /** Toma o proximo job pronto, ou nenhum. Transacao curta, so a tomada. */
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

    /** @return o job, se nao havera nova tentativa */
    @Transactional
    public Optional<JobEntity> markFailed(UUID jobId, String error) {
        return repository.findById(jobId).filter(job -> job.fail(error, clock.instant()));
    }
}
