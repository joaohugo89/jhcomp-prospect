package com.jhcomp.prospect.shared.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o polling do {@link JobWorker}.
 *
 * <p>Desligado no perfil de teste: um worker rodando sozinho durante os testes
 * tornaria o resultado dependente de tempo.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "jhcomp.jobs.worker.enabled", havingValue = "true", matchIfMissing = true)
class JobSchedulingConfig {
}
