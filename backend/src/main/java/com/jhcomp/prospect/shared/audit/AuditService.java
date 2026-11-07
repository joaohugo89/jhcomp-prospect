package com.jhcomp.prospect.shared.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writing to the audit trail (RNF-008, doc 07 §11).
 *
 * <p>It writes in its own transaction ({@code REQUIRES_NEW}). A refused login
 * attempt has to stay recorded even when the request ends in an error: if the
 * record took part in the caller's transaction, the rollback would erase
 * precisely the evidence worth keeping.
 *
 * <p>It never receives a password, a token or message content. The
 * {@code detail} field is a short, non-sensitive summary (doc 07 §6).
 */
@Service
public class AuditService {

    private static final int MAX_DETAIL_LENGTH = 500;

    private final AuditEventRepository repository;
    private final Clock clock;

    // Annotated because the class has two argument-taking constructors:
    // without the annotation Spring cannot choose and fails to create the
    // bean.
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
     * The data of an audited event.
     *
     * <p>{@code organizationId} and {@code actorUserId} may be null: a login
     * failure happens before there is any known user or organisation.
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
         * A login failure.
         *
         * <p>The reason recorded is generic on purpose. The e-mail attempted
         * is not stored: it would be personal data about someone who may not
         * even be a user of the system, collected with no necessary purpose
         * (doc 07 §2).
         */
        public static AuditEntry loginFailure(String ipAddress, String reason) {
            return new AuditEntry(
                    null, null, AuditAction.LOGIN, AuditOutcome.FAILURE, null, null, ipAddress, reason);
        }
    }
}
