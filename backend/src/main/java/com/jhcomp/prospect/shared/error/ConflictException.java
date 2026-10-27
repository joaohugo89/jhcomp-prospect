package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * Operacao valida em formato, mas incompativel com o estado atual do recurso:
 * transicao de status nao permitida, empresa arquivada, oportunidade ja aberta.
 */
public class ConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(ApiErrorType.CONFLICT, 409, message);
    }
}
