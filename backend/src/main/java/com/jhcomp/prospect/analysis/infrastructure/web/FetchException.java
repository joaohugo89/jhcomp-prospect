package com.jhcomp.prospect.analysis.infrastructure.web;

/**
 * A request that obtained no HTTP response.
 *
 * <p>{@code code} is a short reason ({@code DNS}, {@code BLOCKED_ADDRESS},
 * {@code TIMEOUT}, …) that becomes evidence; the library's message is not
 * kept, because it can cite internal detail.
 */
public class FetchException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String code;

    public FetchException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
