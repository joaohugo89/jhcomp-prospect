package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * The current password supplied does not match, during a password change
 * (D28).
 *
 * <p>400, not 401: the session is still valid. A 401 would make the interface
 * treat it as an expired session and return to login.
 */
public class IncorrectPasswordException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public IncorrectPasswordException() {
        super(ApiErrorType.INVALID_CREDENTIALS, 400, "Current password is incorrect");
    }
}
