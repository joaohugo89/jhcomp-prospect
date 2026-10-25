package com.jhcomp.prospect.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.jhcomp.prospect.identity.domain.OrganizationRole;

/**
 * Principal autenticado.
 *
 * <p>Carrega a organizacao ativa e o papel resolvidos no login, a partir da
 * associacao persistida. O cliente nunca informa a organizacao (RF-002,
 * doc 03 §5): ela e sempre derivada daqui.
 */
public final class AuthenticatedUser implements UserDetails {

    private final UUID userId;
    private final UUID organizationId;
    private final String email;
    private final String displayName;
    private final OrganizationRole role;
    private final transient String passwordHash;

    public AuthenticatedUser(
            UUID userId,
            UUID organizationId,
            String email,
            String displayName,
            OrganizationRole role,
            String passwordHash) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
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
     * Remove o hash antes de a sessao ser serializada.
     *
     * <p>O principal e guardado na sessao HTTP; manter o hash ali o expoe sem
     * necessidade.
     */
    public AuthenticatedUser withoutCredentials() {
        return new AuthenticatedUser(userId, organizationId, email, displayName, role, "");
    }

    /** Nao inclui e-mail nem hash (RNF-007). */
    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + ", organizationId=" + organizationId + ", role=" + role + "]";
    }
}
