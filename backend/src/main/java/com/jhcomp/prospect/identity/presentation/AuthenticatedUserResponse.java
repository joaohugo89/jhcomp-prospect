package com.jhcomp.prospect.identity.presentation;

import java.util.UUID;

import com.jhcomp.prospect.identity.domain.OrganizationRole;
import com.jhcomp.prospect.shared.security.AuthenticatedUser;

/**
 * The response of {@code /auth/login} and {@code /auth/me}.
 *
 * <p>It exposes the active organisation for display only. Sending that value
 * back in another request grants no access whatsoever: authorisation always
 * uses the session's organisation (RF-002).
 */
public record AuthenticatedUserResponse(
        UUID userId,
        String email,
        String displayName,
        UUID organizationId,
        OrganizationRole role,
        /** A temporary password: the interface goes straight to the password change (D28). */
        boolean passwordChangeRequired) {

    public static AuthenticatedUserResponse from(AuthenticatedUser user) {
        return new AuthenticatedUserResponse(
                user.userId(), user.email(), user.displayName(), user.organizationId(), user.role(),
                user.passwordChangeRequired());
    }
}
