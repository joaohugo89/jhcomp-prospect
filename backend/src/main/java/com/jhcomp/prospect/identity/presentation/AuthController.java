package com.jhcomp.prospect.identity.presentation;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.identity.application.MembershipUserDetailsService;
import com.jhcomp.prospect.identity.application.PasswordService;
import com.jhcomp.prospect.shared.audit.AuditAction;
import com.jhcomp.prospect.shared.audit.AuditOutcome;
import com.jhcomp.prospect.shared.audit.AuditService;
import com.jhcomp.prospect.shared.error.InvalidCredentialsException;
import com.jhcomp.prospect.shared.error.TooManyAttemptsException;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;
import com.jhcomp.prospect.shared.security.LoginAttemptLimiter;
import com.jhcomp.prospect.shared.security.OrganizationContext;
import com.jhcomp.prospect.shared.web.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Session authentication (RF-001, RF-002, decision D1).
 *
 * <p>There is no {@code /auth/refresh}: decision D1 ruled out short-lived
 * tokens, so there is nothing to renew.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final OrganizationContext organizationContext;
    private final AuditService auditService;
    private final PasswordService passwordService;
    private final Clock clock;

    AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            LoginAttemptLimiter loginAttemptLimiter,
            OrganizationContext organizationContext,
            AuditService auditService,
            PasswordService passwordService,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.organizationContext = organizationContext;
        this.auditService = auditService;
        this.passwordService = passwordService;
        this.clock = clock;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticatedUserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String email = MembershipUserDetailsService.normalizeEmail(request.email());
        String clientIp = ClientIpResolver.resolve(httpRequest);

        // Reserves and records the attempt in the same atomic step. Checking
        // the limit and only then recording the failure would let concurrent
        // requests all pass the check before any of them was counted.
        if (!loginAttemptLimiter.tryAcquire(email, clientIp)) {
            auditService.record(AuditService.AuditEntry.loginFailure(clientIp, "RATE_LIMITED"));
            throw new TooManyAttemptsException();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException exception) {
            // The limiter already counted the attempt at reservation. In the
            // audit trail the reason is generic: telling "no such e-mail" from
            // "wrong password" would be harmless there, but a single path
            // reduces the chance of the difference leaking into the response.
            auditService.record(AuditService.AuditEntry.loginFailure(clientIp, "INVALID_CREDENTIALS"));
            throw new InvalidCredentialsException();
        }

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        // Session fixation protection: the pre-login id stops being valid,
        // which prevents an id planted earlier by an attacker from coming to
        // represent the authenticated session.
        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        httpRequest.getSession(true);

        // The password hash is never kept in the session.
        storeInSession(user.forSession(clock.instant()), httpRequest, httpResponse);

        loginAttemptLimiter.recordSuccess(email, clientIp);
        auditService.record(
                AuditService.AuditEntry.loginSuccess(user.organizationId(), user.userId(), clientIp));

        return ResponseEntity.ok(AuthenticatedUserResponse.from(user));
    }

    /**
     * Changing your own password (D28). The only route, besides
     * {@code /auth/me} and logout, open to someone holding a temporary
     * password.
     *
     * <p>It uses the same limiter as login: the current password is checked
     * here, and without a limit this route would become a password oracle for
     * whoever steals the session.
     */
    @PostMapping("/password")
    public AuthenticatedUserResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        AuthenticatedUser user = organizationContext.requireCurrentUser();
        String clientIp = ClientIpResolver.resolve(httpRequest);
        if (!loginAttemptLimiter.tryAcquire(user.email(), clientIp)) {
            throw new TooManyAttemptsException();
        }

        Instant changedAt = passwordService.changeOwnPassword(
                user, request.currentPassword(), request.newPassword(), clientIp);
        loginAttemptLimiter.recordSuccess(user.email(), clientIp);

        // A new session id, as at login, and a principal carrying the instant
        // of the change: this user's other sessions drop, this one survives.
        httpRequest.changeSessionId();
        AuthenticatedUser updated = user.afterPasswordChange(changedAt);
        storeInSession(updated, httpRequest, httpResponse);
        return AuthenticatedUserResponse.from(updated);
    }

    private void storeInSession(AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /** The current session's user and active organisation. */
    @GetMapping("/me")
    public AuthenticatedUserResponse me() {
        return AuthenticatedUserResponse.from(organizationContext.requireCurrentUser());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        // Read the user before invalidating the session: afterwards there is
        // no way to know who ended it.
        organizationContext.currentUser().ifPresent(user -> auditService.record(new AuditService.AuditEntry(
                user.organizationId(),
                user.userId(),
                AuditAction.LOGOUT,
                AuditOutcome.SUCCESS,
                null,
                null,
                ClientIpResolver.resolve(httpRequest),
                null)));

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }
}
