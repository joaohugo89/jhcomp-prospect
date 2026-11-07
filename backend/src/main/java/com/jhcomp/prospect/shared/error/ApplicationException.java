package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * The base of the exceptions the API handles.
 *
 * <p>Each subclass declares its error type and status, so the global handler
 * needs no chain of {@code instanceof}.
 *
 * <p>The message is shown to the client: never put internal detail, SQL, a
 * file path or personal data in it (RNF-003, RNF-007).
 */
public abstract class ApplicationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ApiErrorType type;
    private final int status;

    protected ApplicationException(ApiErrorType type, int status, String message) {
        super(message);
        this.type = type;
        this.status = status;
    }

    public ApiErrorType type() {
        return type;
    }

    public int status() {
        return status;
    }
}
