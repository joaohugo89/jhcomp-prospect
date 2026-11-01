package com.jhcomp.prospect.identity.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Troca da propria senha (D28). A politica da senha nova (tamanho minimo e
 * teto do BCrypt) e conferida no servico; o teto aqui so impede que um corpo
 * enorme chegue ao hash.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "must not be blank") @Size(max = 128, message = "must be at most 128 characters")
        String currentPassword,
        @NotBlank(message = "must not be blank") @Size(max = 128, message = "must be at most 128 characters")
        String newPassword) {

    /** Evita que as senhas aparecam em log ou mensagem de erro (RNF-007). */
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
