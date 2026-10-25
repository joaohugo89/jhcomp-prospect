package com.jhcomp.prospect.identity.presentation;

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
 * Autenticacao por sessao (RF-001, RF-002, decisao D1).
 *
 * <p>Nao existe {@code /auth/refresh}: a decisao D1 descartou token de curta
 * duracao, de modo que nao ha o que renovar.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final OrganizationContext organizationContext;
    private final AuditService auditService;

    AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            LoginAttemptLimiter loginAttemptLimiter,
            OrganizationContext organizationContext,
            AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.organizationContext = organizationContext;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticatedUserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String email = MembershipUserDetailsService.normalizeEmail(request.email());
        String clientIp = ClientIpResolver.resolve(httpRequest);

        if (loginAttemptLimiter.isBlocked(email, clientIp)) {
            auditService.record(AuditService.AuditEntry.loginFailure(clientIp, "RATE_LIMITED"));
            throw new TooManyAttemptsException();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException exception) {
            loginAttemptLimiter.recordFailure(email, clientIp);
            // Motivo generico: distinguir "e-mail inexistente" de "senha
            // errada" na auditoria seria inofensivo, mas manter um unico
            // caminho reduz a chance de a diferenca escapar para a resposta.
            auditService.record(AuditService.AuditEntry.loginFailure(clientIp, "INVALID_CREDENTIALS"));
            throw new InvalidCredentialsException();
        }

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        // Protecao contra fixacao de sessao: o id anterior ao login deixa de
        // ser valido, impedindo que um id previamente plantado pelo atacante
        // passe a representar a sessao autenticada.
        HttpSession existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        httpRequest.getSession(true);

        // O hash de senha nao e guardado na sessao.
        Authentication storedAuthentication = new UsernamePasswordAuthenticationToken(
                user.withoutCredentials(), null, authentication.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(storedAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        loginAttemptLimiter.recordSuccess(email, clientIp);
        auditService.record(
                AuditService.AuditEntry.loginSuccess(user.organizationId(), user.userId(), clientIp));

        return ResponseEntity.ok(AuthenticatedUserResponse.from(user));
    }

    /** Usuario e organizacao ativa da sessao corrente. */
    @GetMapping("/me")
    public AuthenticatedUserResponse me() {
        return AuthenticatedUserResponse.from(organizationContext.requireCurrentUser());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        // Le o usuario antes de invalidar a sessao: depois dela nao ha mais
        // como saber quem encerrou.
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
