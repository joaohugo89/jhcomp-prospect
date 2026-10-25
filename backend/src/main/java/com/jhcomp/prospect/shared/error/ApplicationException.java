package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * Base das excecoes tratadas pela API.
 *
 * <p>Cada subclasse declara o tipo de erro e o status, de modo que o handler
 * global nao precise de uma cadeia de {@code instanceof}.
 *
 * <p>A mensagem e exibida ao cliente: nao colocar nela detalhe interno, SQL,
 * caminho de arquivo nem dado pessoal (RNF-003, RNF-007).
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
