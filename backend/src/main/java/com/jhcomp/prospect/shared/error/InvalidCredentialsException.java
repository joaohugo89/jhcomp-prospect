package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * Invalid credentials.
 *
 * <p>The message is deliberately identical for an unknown e-mail, a wrong
 * password, a disabled user and a suspended organisation: revealing the
 * difference would allow account enumeration, which RF-001's acceptance
 * criterion forbids.
 */
public class InvalidCredentialsException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super(ApiErrorType.INVALID_CREDENTIALS, 401, "Invalid email or password");
    }
}
