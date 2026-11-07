package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * An invalid parameter that the DTO's declarative validation cannot reach,
 * such as a sort field outside the permitted list.
 */
public class InvalidRequestException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super(ApiErrorType.VALIDATION_ERROR, 400, message);
    }
}
