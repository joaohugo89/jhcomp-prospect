package com.jhcomp.prospect.identity.presentation;

import java.util.UUID;

import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;

/**
 * Resposta de {@code /auth/login} e {@code /auth/me}.
 *
 * <p>Expoe a organizacao ativa apenas para exibicao. Enviar este valor de volta
 * em outra requisicao nao concede acesso algum: a autorizacao usa sempre a
 * organizacao da sessao (RF-002).
 */
public record AuthenticatedUserResponse(
        UUID userId,
        String email,
        String displayName,
        UUID organizationId,
        OrganizationRole role,
        /** Senha temporaria: a interface leva direto a troca de senha (D28). */
        boolean passwordChangeRequired) {

    public static AuthenticatedUserResponse from(AuthenticatedUser user) {
        return new AuthenticatedUserResponse(
                user.userId(), user.email(), user.displayName(), user.organizationId(), user.role(),
                user.passwordChangeRequired());
    }
}
