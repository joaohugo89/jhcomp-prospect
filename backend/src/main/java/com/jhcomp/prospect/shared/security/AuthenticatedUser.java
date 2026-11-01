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
 * Principal autenticado.
 *
 * <p>Carrega a organizacao ativa e o papel resolvidos no login, a partir da
 * associacao persistida. O cliente nunca informa a organizacao (RF-002,
 * doc 03 §5): ela e sempre derivada daqui.
 *
 * <p>{@code authenticatedAt} e o instante em que a sessao foi aberta ou teve a
 * senha trocada; a revalidacao encerra a sessao se a senha mudou depois dele
 * (D28). {@code passwordChangeRequired} restringe a sessao a troca de senha.
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
     * Versao guardada na sessao: sem o hash, que nao precisa ficar exposto ali,
     * e com o instante de abertura da sessao.
     */
    public AuthenticatedUser forSession(Instant openedAt) {
        return new AuthenticatedUser(
                userId, organizationId, email, displayName, role, passwordChangeRequired, openedAt, "");
    }

    /**
     * Sessao de quem acabou de trocar a propria senha: sem exigencia de troca
     * e com o instante da troca, para nao ser encerrada pela regra de senha
     * alterada depois do login (D28).
     */
    public AuthenticatedUser afterPasswordChange(Instant changedAt) {
        return new AuthenticatedUser(userId, organizationId, email, displayName, role, false, changedAt, "");
    }

    /** Mesma sessao com papel e exigencia de troca de senha relidos do banco (D21). */
    public AuthenticatedUser withCurrentState(OrganizationRole currentRole, boolean currentPasswordChangeRequired) {
        return new AuthenticatedUser(
                userId, organizationId, email, displayName, currentRole, currentPasswordChangeRequired,
                authenticatedAt, "");
    }

    /** Nao inclui e-mail nem hash (RNF-007). */
    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + ", organizationId=" + organizationId + ", role=" + role + "]";
    }
}
