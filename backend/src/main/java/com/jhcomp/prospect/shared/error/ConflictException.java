package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * An operation that is valid in shape but incompatible with the resource's
 * current state: a transition that is not allowed, an archived company, an
 * opportunity that is already open.
 */
public class ConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(ApiErrorType.CONFLICT, 409, message);
    }
}
