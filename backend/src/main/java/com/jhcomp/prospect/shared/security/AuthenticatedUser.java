package com.jhcomp.prospect.shared.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.jhcomp.prospect.identity.domain.OrganizationRole;

/**
 * The authenticated principal.
 *
 * <p>It carries the active organisation and the role resolved at login from
 * the persisted membership. The client never supplies the organisation
 * (RF-002, doc 03 §5): it is always derived from here.
 *
 * <p>{@code authenticatedAt} is the instant the session was opened, or had its
 * password changed; revalidation ends the session if the password changed
 * after it (D28). {@code passwordChangeRequired} restricts the session to the
 * password change.
 */
public final class AuthenticatedUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final UUID organizationId;
    private final String email;
    private final String displayName;
    private final OrganizationRole role;
    private final boolean passwordChangeRequired;
    private final Instant authenticatedAt;
    private final transient String passwordHash;

    public AuthenticatedUser(
            UUID userId,
            UUID organizationId,
            String email,
            String displayName,
            OrganizationRole role,
            boolean passwordChangeRequired,
            Instant authenticatedAt,
            String passwordHash) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.passwordChangeRequired = passwordChangeRequired;
        this.authenticatedAt = authenticatedAt;
        this.passwordHash = passwordHash;
    }

    public UUID userId() {
        return userId;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public OrganizationRole role() {
        return role;
    }

    public boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    public Instant authenticatedAt() {
        return authenticatedAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /**
     * The version kept in the session: without the hash, which has no reason
     * to be exposed there, and with the instant the session was opened.
     */
    public AuthenticatedUser forSession(Instant openedAt) {
        return new AuthenticatedUser(
                userId, organizationId, email, displayName, role, passwordChangeRequired, openedAt, "");
    }

    /**
     * The session of someone who has just changed their own password: with no
     * change required, and carrying the instant of the change, so it is not
     * ended by the rule about a password changed after login (D28).
     */
    public AuthenticatedUser afterPasswordChange(Instant changedAt) {
        return new AuthenticatedUser(userId, organizationId, email, displayName, role, false, changedAt, "");
    }

    /** The same session with role and password-change requirement re-read from the database (D21). */
    public AuthenticatedUser withCurrentState(OrganizationRole currentRole, boolean currentPasswordChangeRequired) {
        return new AuthenticatedUser(
                userId, organizationId, email, displayName, currentRole, currentPasswordChangeRequired,
                authenticatedAt, "");
    }

    /** It includes neither e-mail nor hash (RNF-007). */
    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + ", organizationId=" + organizationId + ", role=" + role + "]";
    }
}
