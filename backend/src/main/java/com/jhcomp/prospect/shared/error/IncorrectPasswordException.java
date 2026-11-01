package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * Senha atual informada nao confere, na troca de senha (D28).
 *
 * <p>400, e nao 401: a sessao continua valida. Um 401 faria a interface
 * tratar como sessao expirada e voltar ao login.
 */
public class IncorrectPasswordException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public IncorrectPasswordException() {
        super(ApiErrorType.INVALID_CREDENTIALS, 400, "Current password is incorrect");
    }
}
