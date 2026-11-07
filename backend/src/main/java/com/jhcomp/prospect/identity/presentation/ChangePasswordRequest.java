package com.jhcomp.prospect.identity.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changing your own password (D28). The new password's policy — minimum
 * length and the BCrypt ceiling — is checked in the service; the limit here
 * only stops an enormous body from reaching the hash.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "must not be blank") @Size(max = 128, message = "must be at most 128 characters")
        String currentPassword,
        @NotBlank(message = "must not be blank") @Size(max = 128, message = "must be at most 128 characters")
        String newPassword) {

    /** Keeps the passwords out of logs and error messages (RNF-007). */
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
