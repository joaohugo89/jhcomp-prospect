package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

public class TooManyAttemptsException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public TooManyAttemptsException() {
        super(ApiErrorType.TOO_MANY_REQUESTS, 429, "Too many attempts. Try again later");
    }
}
