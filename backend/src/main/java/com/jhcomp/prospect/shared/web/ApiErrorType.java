package com.jhcomp.prospect.shared.web;

/**
 * Codigos de erro da API. Fazem parte do contrato: o frontend pode decidir
 * comportamento a partir deles, portanto renomear um valor e mudanca
 * incompativel.
 */
public enum ApiErrorType {
    VALIDATION_ERROR("Request validation failed"),
    AUTHENTICATION_REQUIRED("Authentication required"),
    INVALID_CREDENTIALS("Invalid credentials"),
    SESSION_EXPIRED("Session expired"),
    FORBIDDEN("Operation not permitted"),
    RESOURCE_NOT_FOUND("Resource not found"),
    CONFLICT("Conflicting state"),
    TOO_MANY_REQUESTS("Too many requests"),
    MALFORMED_REQUEST("Malformed request"),
    METHOD_NOT_ALLOWED("Method not allowed"),
    PAYLOAD_TOO_LARGE("Payload too large"),
    AI_PROVIDER_ERROR("AI provider error"),
    UNEXPECTED_ERROR("Unexpected error");

    private final String title;

    ApiErrorType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
