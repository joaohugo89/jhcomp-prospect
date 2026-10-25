package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

public class UnauthenticatedException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public UnauthenticatedException() {
        super(ApiErrorType.AUTHENTICATION_REQUIRED, 401, "Authentication is required for this operation");
    }
}
