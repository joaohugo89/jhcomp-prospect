package com.jhcomp.prospect.shared.security;

import java.io.IOException;
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
 * Revalida a associacao do usuario em cada requisicao autenticada.
 *
 * <p>Sem este filtro, {@code organizationId} e {@code role} ficariam
 * congelados na sessao por toda a sua duracao. Revogar uma associacao,
 * desabilitar um usuario, suspender uma organizacao ou rebaixar um
 * administrador a operador nao teria efeito algum ate a sessao expirar — o
 * usuario continuaria operando com o privilegio antigo por horas. Isso
 * contraria o critério de aceite do RF-003, que exige que operacoes
 * administrativas sejam bloqueadas para quem nao tem permissao.
 *
 * <p>Quando a associacao deixa de ser valida, a sessao e encerrada e o
 * contexto limpo; a requisicao segue na cadeia e a propria autorizacao
 * responde 401 no formato padrao da API, sem duplicar aqui a escrita do JSON.
 *
 * <p>Quando apenas o papel mudou, o principal e substituido e regravado na
 * sessao, de modo que a mudanca passa a valer imediatamente.
 *
 * <p>Custo: uma consulta indexada por requisicao autenticada. No volume do MVP
 * isso e irrelevante, e a alternativa — revalidar a cada N minutos — deixaria
 * uma janela em que o privilegio revogado ainda funciona. Se o volume crescer,
 * o lugar de introduzir intervalo ou cache e aqui.
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
            // Requisicao anonima ou principal de outro tipo: nada a revalidar.
            filterChain.doFilter(request, response);
            return;
        }

        List<OrganizationMemberEntity> memberships =
                memberRepository.findActiveMembershipsByUserId(user.userId());

        if (memberships.size() != 1) {
            // Zero: associacao revogada, usuario desabilitado ou organizacao
            // suspensa. Mais de uma: organizacao ambigua, mesma recusa aplicada
            // no login.
            log.info("Sessao encerrada: associacao ativa invalida para o usuario {}", user.userId());
            endSession(request);
            filterChain.doFilter(request, response);
            return;
        }

        OrganizationMemberEntity membership = memberships.getFirst();

        if (!membership.getOrganization().getId().equals(user.organizationId())) {
            // A organizacao do usuario mudou: a sessao atual nao representa
            // mais um contexto valido e precisa ser refeita pelo login.
            log.warn("Sessao encerrada: organizacao divergente para o usuario {}", user.userId());
            endSession(request);
            filterChain.doFilter(request, response);
            return;
        }

        if (membership.getRole() != user.role()) {
            log.info(
                    "Papel atualizado na sessao do usuario {}: {} -> {}",
                    user.userId(),
                    user.role(),
                    membership.getRole());
            refreshPrincipal(request, response, user, membership);
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
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticatedUser currentUser,
            OrganizationMemberEntity membership) {

        AuthenticatedUser refreshed = new AuthenticatedUser(
                currentUser.userId(),
                membership.getOrganization().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getDisplayName(),
                membership.getRole(),
                // O hash de senha nao e guardado na sessao.
                "");

        Authentication updated =
                new UsernamePasswordAuthenticationToken(refreshed, null, refreshed.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updated);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
