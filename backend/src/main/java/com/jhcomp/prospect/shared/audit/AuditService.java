package com.jhcomp.prospect.shared.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro da trilha de auditoria (RNF-008, doc 07 §11).
 *
 * <p>Grava em transacao propria ({@code REQUIRES_NEW}). Uma tentativa de login
 * recusada precisa ficar registrada mesmo que a requisicao termine em erro: se
 * o registro participasse da transacao do chamador, o rollback apagaria
 * justamente a evidencia que se quer guardar.
 *
 * <p>Nunca recebe senha, token nem conteudo de mensagem. O campo
 * {@code detail} e um resumo curto e nao sensivel (doc 07 §6).
 */
@Service
public class AuditService {

    private static final int MAX_DETAIL_LENGTH = 500;

    private final AuditEventRepository repository;
    private final Clock clock;

    // Anotado porque a classe tem dois construtores com argumentos: sem a
    // anotacao o Spring nao consegue escolher e falha ao criar o bean.
    @Autowired
    AuditService(AuditEventRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        repository.save(new AuditEventEntity(
                UUID.randomUUID(),
                entry.organizationId(),
                entry.actorUserId(),
                entry.action(),
                entry.outcome(),
                entry.resourceType(),
                entry.resourceId(),
                entry.ipAddress(),
                truncate(entry.detail()),
                clock.instant()));
    }

    private static String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL_LENGTH) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL_LENGTH);
    }

    /**
     * Dados de um evento auditado.
     *
     * <p>{@code organizationId} e {@code actorUserId} podem ser nulos: uma
     * falha de login ocorre antes de haver usuario ou organizacao conhecidos.
     */
    public record AuditEntry(
            UUID organizationId,
            UUID actorUserId,
            AuditAction action,
            AuditOutcome outcome,
            String resourceType,
            String resourceId,
            String ipAddress,
            String detail) {

        public static AuditEntry loginSuccess(UUID organizationId, UUID userId, String ipAddress) {
            return new AuditEntry(
                    organizationId, userId, AuditAction.LOGIN, AuditOutcome.SUCCESS, null, null, ipAddress, null);
        }

        /**
         * Falha de login.
         *
         * <p>O motivo registrado e generico de proposito. O e-mail tentado nao
         * e armazenado: seria dado pessoal de alguem que pode nem ser usuario
         * do sistema, coletado sem finalidade necessaria (doc 07 §2).
         */
        public static AuditEntry loginFailure(String ipAddress, String reason) {
            return new AuditEntry(
                    null, null, AuditAction.LOGIN, AuditOutcome.FAILURE, null, null, ipAddress, reason);
        }
    }
}
