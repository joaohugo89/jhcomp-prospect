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

        // Reserva e contabiliza a tentativa no mesmo passo atomico. Verificar
        // o limite e so depois registrar a falha permitiria que requisicoes
        // concorrentes passassem todas pela verificacao antes de qualquer uma
        // ser contabilizada.
        if (!loginAttemptLimiter.tryAcquire(email, clientIp)) {
            auditService.record(AuditService.AuditEntry.loginFailure(clientIp, "RATE_LIMITED"));
            throw new TooManyAttemptsException();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException exception) {
            // O limitador ja contou a tentativa na reserva. Na auditoria, o
            // motivo e generico: distinguir "e-mail inexistente" de "senha
            // errada" ali seria inofensivo, mas um caminho unico reduz a
            // chance de a diferenca escapar para a resposta.
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
        storeInSession(user.forSession(clock.instant()), httpRequest, httpResponse);

        loginAttemptLimiter.recordSuccess(email, clientIp);
        auditService.record(
                AuditService.AuditEntry.loginSuccess(user.organizationId(), user.userId(), clientIp));

        return ResponseEntity.ok(AuthenticatedUserResponse.from(user));
    }

    /**
     * Troca da propria senha (D28). Unica rota, alem de {@code /auth/me} e
     * logout, aberta a quem tem senha temporaria.
     *
     * <p>Usa o mesmo limitador do login: a senha atual e conferida aqui, e sem
     * limite esta rota viraria um oraculo de senha para quem rouba a sessao.
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

        // Novo id de sessao, como no login, e o principal com o instante da
        // troca: as outras sessoes deste usuario caem, esta continua.
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
