package com.jhcomp.prospect.identity.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jhcomp.prospect.identity.domain.PasswordPolicy;
import com.jhcomp.prospect.identity.infrastructure.persistence.UserEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.UserRepository;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditOutcome;
import com.jhcomp.prospect.shared.audit.AuditService;
import com.jhcomp.prospect.shared.audit.AuditService.AuditEntry;
import com.jhcomp.prospect.shared.error.IncorrectPasswordException;
import com.jhcomp.prospect.shared.error.InvalidRequestException;
import com.jhcomp.prospect.shared.error.ResourceNotFoundException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;

/**
 * Changing your own password (RF-003, D28).
 *
 * <p>It requires the current password, even when that password is temporary:
 * whoever holds only the open session, and not the password, cannot take over
 * the account by changing it. The attempt limit lives in the controller,
 * which knows the IP.
 */
@Service
public class PasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    PasswordService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Stores the new password and returns the instant of the change. The
     * user's other sessions, opened before it, end on their next request.
     */
    @Transactional
    public Instant changeOwnPassword(AuthenticatedUser user, String currentPassword, String newPassword, String ipAddress) {
        UserEntity account = userRepository.findById(user.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User"));
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            auditService.record(new AuditEntry(user.organizationId(), user.userId(), AuditAction.PASSWORD_CHANGE,
                    AuditOutcome.FAILURE, "User", user.userId().toString(), ipAddress, "INCORRECT_CURRENT_PASSWORD"));
            throw new IncorrectPasswordException();
        }
        PasswordPolicy.violation(newPassword).ifPresent(violation -> {
            throw new InvalidRequestException(violation);
        });
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new InvalidRequestException("New password must differ from the current one");
        }

        account.replacePassword(passwordEncoder.encode(newPassword), false, clock.instant());
        auditService.record(new AuditEntry(user.organizationId(), user.userId(), AuditAction.PASSWORD_CHANGE,
                AuditOutcome.SUCCESS, "User", user.userId().toString(), ipAddress, null));
        // The very value written to the database goes into the session.
        return account.getCredentialsChangedAt();
    }
}
