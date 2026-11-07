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
 * A user. The password hash never leaves this layer (doc 07 §4).
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

    /** A temporary password: it must be changed before any other operation (D28). */
    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    /** The last password change or reset; a session older than it is ended (D28). */
    @Column(name = "credentials_changed_at")
    private Instant credentialsChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
        // Required by JPA.
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
     * Stores a new password. {@code changeRequired} is true when an
     * administrator set it (a temporary password), false when the user set it
     * themselves.
     */
    public void replacePassword(String newPasswordHash, boolean changeRequired, Instant now) {
        this.passwordHash = newPasswordHash;
        this.passwordChangeRequired = changeRequired;
        // timestamptz precision. PostgreSQL rounds, rather than truncates,
        // anything below the microsecond: without this the stored value could
        // land after the instant kept in the session of whoever changed the
        // password, and revalidation would end precisely that session.
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

    /** It includes neither e-mail nor hash: logs and error messages must not leak personal data (RNF-007). */
    @Override
    public String toString() {
        return "UserEntity[id=" + getId() + ", status=" + status + "]";
    }
}
