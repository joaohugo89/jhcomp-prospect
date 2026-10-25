package com.jhcomp.prospect.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.jhcomp.prospect.shared.error.UnauthenticatedException;

/**
 * Unica fonte da organizacao ativa (RF-002, RNF-001).
 *
 * <p>Todo caso de uso que toque dado privado obtem a organizacao aqui, e nunca
 * do corpo, do path ou de um parametro da requisicao. Confiar em
 * {@code organizationId} enviado pelo cliente e o que a especificacao proibe
 * explicitamente (doc 03 §5, doc 07 §5).
 */
@Component
public class OrganizationContext {

    /** Organizacao ativa, ou falha se nao houver usuario autenticado. */
    public UUID requireOrganizationId() {
        // Deriva do usuario exigido em vez de mapear o Optional: encadear
        // map(AuthenticatedUser::organizationId) faz as ferramentas de analise
        // de nulidade reclamarem do receptor da referencia de metodo, sem
        // ganho de clareza.
        return requireCurrentUser().organizationId();
    }

    /** Usuario autenticado, ou falha se nao houver. */
    public AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(UnauthenticatedException::new);
    }

    public Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }

        // Principal anonimo do Spring Security cai aqui.
        return Optional.empty();
    }
}
