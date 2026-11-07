package com.jhcomp.prospect.shared.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberEntity;
import com.jhcomp.prospect.identity.infrastructure.persistence.OrganizationMemberRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Revalidates the user's membership on every authenticated request.
 *
 * <p>Without this filter, {@code organizationId} and {@code role} would stay
 * frozen in the session for its whole lifetime. Revoking a membership,
 * disabling a user, suspending an organisation or demoting an administrator to
 * operator would have no effect until the session expired — the user would go
 * on working with the old privilege for hours. That contradicts the acceptance
 * criterion of RF-003, which requires administrative operations to be refused
 * to whoever lacks permission.
 *
 * <p>When the membership stops being valid, the session is ended and the
 * context cleared; the request continues down the chain and authorisation
 * itself answers 401 in the API's standard format, rather than duplicating the
 * JSON writing here.
 *
 * <p>When only the role changed, the principal is replaced and written back to
 * the session, so the change takes effect immediately.
 *
 * <p>Cost: one indexed query per authenticated request. At MVP volume that is
 * irrelevant, and the alternative — revalidating every N minutes — would leave
 * a window in which a revoked privilege still works. If volume grows, this
 * filter is where an interval or a cache belongs.
 */
public class MembershipRevalidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MembershipRevalidationFilter.class);

    private final OrganizationMemberRepository memberRepository;
    private final SecurityContextRepository securityContextRepository;

    public MembershipRevalidationFilter(
            OrganizationMemberRepository memberRepository, SecurityContextRepository securityContextRepository) {
        this.memberRepository = memberRepository;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            // An anonymous request, or a principal of another type: nothing to revalidate.
            filterChain.doFilter(request, response);
            return;
        }

        List<OrganizationMemberEntity> memberships =
                memberRepository.findActiveMembershipsByUserId(user.userId());

        if (memberships.size() != 1) {
            // Zero: membership revoked, user disabled, or organisation
            // suspended. More than one: an ambiguous organisation, the same
            // refusal applied at login.
            log.info("Sessao encerrada: associacao ativa invalida para o usuario {}", user.userId());
            endSession(request);
            filterChain.doFilter(request, response);
            return;
        }

        OrganizationMemberEntity membership = memberships.getFirst();

        if (!membership.getOrganization().getId().equals(user.organizationId())) {
            // The user's organisation changed: the current session no longer
            // represents a valid context and has to be rebuilt by logging in.
            log.warn("Sessao encerrada: organizacao divergente para o usuario {}", user.userId());
            endSession(request);
            filterChain.doFilter(request, response);
            return;
        }

        Instant credentialsChangedAt = membership.getUser().getCredentialsChangedAt();
        if (credentialsChangedAt != null
                && (user.authenticatedAt() == null || user.authenticatedAt().isBefore(credentialsChangedAt))) {
            // The password was changed or reset after this session was opened:
            // whoever held the old password does not stay in (D28).
            log.info("Sessao encerrada: senha alterada depois do login do usuario {}", user.userId());
            endSession(request);
            filterChain.doFilter(request, response);
            return;
        }

        boolean passwordChangeRequired = membership.getUser().isPasswordChangeRequired();
        if (membership.getRole() != user.role() || passwordChangeRequired != user.passwordChangeRequired()) {
            log.info(
                    "Sessao do usuario {} atualizada: papel {} -> {}, troca de senha exigida {}",
                    user.userId(),
                    user.role(),
                    membership.getRole(),
                    passwordChangeRequired);
            refreshPrincipal(request, response, user.withCurrentState(membership.getRole(), passwordChangeRequired));
        }

        filterChain.doFilter(request, response);
    }

    private void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private void refreshPrincipal(
            HttpServletRequest request, HttpServletResponse response, AuthenticatedUser refreshed) {

        Authentication updated =
                new UsernamePasswordAuthenticationToken(refreshed, null, refreshed.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updated);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
