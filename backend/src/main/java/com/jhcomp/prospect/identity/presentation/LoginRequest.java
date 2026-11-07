package com.jhcomp.prospect.identity.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login credentials (RF-001).
 *
 * <p>The password length limit is not cosmetic: without it, an enormous
 * password would make BCrypt work on every attempt, which enables a denial of
 * service through CPU consumption.
 */
public record LoginRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 128, message = "must be between 8 and 128 characters")
        String password) {

    /** Keeps the password out of logs and error messages (RNF-007). */
    @Override
    public String toString() {
        return "LoginRequest[email=***, password=***]";
    }
}
