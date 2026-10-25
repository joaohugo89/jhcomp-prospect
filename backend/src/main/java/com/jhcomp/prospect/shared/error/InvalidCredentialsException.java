package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * Credenciais invalidas.
 *
 * <p>A mensagem e deliberadamente identica para e-mail inexistente, senha
 * errada, usuario desabilitado e organizacao suspensa: revelar a diferenca
 * permitiria enumerar contas, o que o critério de aceite do RF-001 proibe.
 */
public class InvalidCredentialsException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super(ApiErrorType.INVALID_CREDENTIALS, 401, "Invalid email or password");
    }
}
