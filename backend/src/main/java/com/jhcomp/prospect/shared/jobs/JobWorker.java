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
 * Consome a fila de jobs (D15).
 *
 * <p>O agendamento so existe quando {@code jhcomp.jobs.worker.enabled} e
 * verdadeiro ({@link JobSchedulingConfig}). Nos testes ele fica desligado e o
 * teste chama {@link #runPending()} quando quer, sem depender de tempo.
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    /** Teto por rodada: uma fila grande nao monopoliza o worker. */
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

    /** @return quantos jobs foram executados nesta rodada */
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
            // O detalhe vai para o log do servidor; no job fica so a classe do
            // erro, que nao carrega dado da empresa nem mensagem interna.
            log.warn("Job {} ({}) falhou na tentativa {}", job.getId(), job.getJobType(), job.getAttempts(), exception);
            queue.markFailed(job.getId(), exception.getClass().getSimpleName())
                    .ifPresent(handler::onGiveUp);
        }
    }
}
