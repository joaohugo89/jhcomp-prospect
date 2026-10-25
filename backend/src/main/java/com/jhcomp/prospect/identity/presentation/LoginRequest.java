package com.jhcomp.prospect.identity.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciais de login (RF-001).
 *
 * <p>O limite de tamanho da senha nao e cosmetico: sem ele, uma senha enorme
 * forcaria o BCrypt a trabalhar a cada tentativa, o que viabiliza negacao de
 * servico por consumo de CPU.
 */
public record LoginRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 128, message = "must be between 8 and 128 characters")
        String password) {

    /** Evita que a senha apareca em log ou mensagem de erro (RNF-007). */
    @Override
    public String toString() {
        return "LoginRequest[email=***, password=***]";
    }
}
