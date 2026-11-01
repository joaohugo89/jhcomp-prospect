package com.jhcomp.prospect.identity.infrastructure.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.jhcomp.prospect.identity.domain.UserStatus;
import com.jhcomp.prospect.shared.persistence.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Usuario. O hash de senha nunca sai desta camada (doc 07 §4).
 */
@Entity
@Table(name = "app_user")
public class UserEntity extends AssignedIdEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    /** Senha temporaria: precisa ser trocada antes de qualquer outra operacao (D28). */
    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    /** Ultima troca ou redefinicao de senha; sessao anterior a ele e encerrada (D28). */
    @Column(name = "credentials_changed_at")
    private Instant credentialsChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
        // Exigido pelo JPA.
    }

    public UserEntity(
            UUID id,
            String email,
            String passwordHash,
            String displayName,
            UserStatus status,
            boolean passwordChangeRequired,
            Instant now) {
        super(id);
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = status;
        this.passwordChangeRequired = passwordChangeRequired;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Grava nova senha. {@code changeRequired} verdadeiro quando quem definiu
     * foi o administrador (senha temporaria), falso quando foi o proprio usuario.
     */
    public void replacePassword(String newPasswordHash, boolean changeRequired, Instant now) {
        this.passwordHash = newPasswordHash;
        this.passwordChangeRequired = changeRequired;
        // Precisao do timestamptz. O PostgreSQL arredonda, e nao trunca, o que
        // passa de microssegundo: sem isto o valor gravado podia ficar depois do
        // instante guardado na sessao de quem trocou a senha, e a revalidacao
        // encerrava justamente essa sessao.
        this.credentialsChangedAt = now.truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = now;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public Instant getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Nao inclui e-mail nem hash: log e mensagem de erro nao devem vazar dado pessoal (RNF-007). */
    @Override
    public String toString() {
        return "UserEntity[id=" + getId() + ", status=" + status + "]";
    }
}
