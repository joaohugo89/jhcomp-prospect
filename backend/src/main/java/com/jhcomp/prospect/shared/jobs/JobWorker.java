package com.jhcomp.prospect.shared.jobs;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consumes the job queue (D15).
 *
 * <p>The schedule only exists when {@code jhcomp.jobs.worker.enabled} is true
 * ({@link JobSchedulingConfig}). In tests it is switched off and the test
 * calls {@link #runPending()} when it wants to, without depending on time.
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    /** A cap per round: a large queue does not monopolise the worker. */
    static final int MAX_JOBS_PER_RUN = 10;

    private final JobQueue queue;
    private final Map<String, JobHandler> handlers;

    JobWorker(JobQueue queue, List<JobHandler> handlers) {
        this.queue = queue;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(JobHandler::jobType, Function.identity()));
    }

    @Scheduled(fixedDelayString = "${jhcomp.jobs.poll-interval:2s}", initialDelayString = "${jhcomp.jobs.poll-interval:2s}")
    void poll() {
        runPending();
    }

    /** @return how many jobs ran in this round */
    public int runPending() {
        int executed = 0;
        while (executed < MAX_JOBS_PER_RUN) {
            var next = queue.claimNext();
            if (next.isEmpty()) {
                break;
            }
            run(next.get());
            executed++;
        }
        return executed;
    }

    private void run(JobEntity job) {
        JobHandler handler = handlers.get(job.getJobType());
        if (handler == null) {
            log.error("Job {} de tipo desconhecido {}", job.getId(), job.getJobType());
            queue.markFailed(job.getId(), "Unknown job type");
            return;
        }
        try {
            handler.handle(job);
            queue.markSucceeded(job.getId());
        } catch (RuntimeException exception) {
            // The detail goes to the server log; the job keeps only the error
            // class, which carries neither company data nor an internal
            // message.
            log.warn("Job {} ({}) falhou na tentativa {}", job.getId(), job.getJobType(), job.getAttempts(), exception);
            queue.markFailed(job.getId(), exception.getClass().getSimpleName())
                    .ifPresent(handler::onGiveUp);
        }
    }
}
