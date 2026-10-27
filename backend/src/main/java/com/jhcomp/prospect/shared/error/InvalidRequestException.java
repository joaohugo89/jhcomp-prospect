package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * Parametro invalido que a validacao declarativa do DTO nao alcanca, como um
 * campo de ordenacao fora da lista permitida.
 */
public class InvalidRequestException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(ApiErrorType.VALIDATION_ERROR, 400, message);
    }
}
