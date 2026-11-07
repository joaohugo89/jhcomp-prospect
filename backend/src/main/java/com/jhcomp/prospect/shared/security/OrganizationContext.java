package com.jhcomp.prospect.shared.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.jhcomp.prospect.shared.error.UnauthenticatedException;

/**
 * The single source of the active organisation (RF-002, RNF-001).
 *
 * <p>Every use case that touches private data obtains the organisation here,
 * never from the body, the path or a request parameter. Trusting an
 * {@code organizationId} sent by the client is exactly what the specification
 * forbids explicitly (doc 03 §5, doc 07 §5).
 */
@Component
public class OrganizationContext {

    /** The active organisation, or a failure when there is no authenticated user. */
    public UUID requireOrganizationId() {
        // Derived from the required user rather than by mapping the Optional:
        // chaining map(AuthenticatedUser::organizationId) makes null-analysis
        // tools complain about the method reference's receiver, with no gain
        // in clarity.
        return requireCurrentUser().organizationId();
    }

    /** The authenticated user, or a failure when there is none. */
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

        // Spring Security's anonymous principal lands here.
        return Optional.empty();
    }
}
