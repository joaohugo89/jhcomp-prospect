package com.jhcomp.prospect.shared.web;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.jhcomp.prospect.shared.error.ApplicationException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Global error handling (RNF-003, doc 05 §1).
 *
 * <p>It extends {@link ResponseEntityExceptionHandler} so that Spring MVC's
 * own exceptions — unknown route, unsupported method, malformed body,
 * validation failure — come out in the same shape as the application's. Without
 * that, the API would answer in two different formats depending on where the
 * error was born.
 *
 * <p>No response carries a stack trace or an internal message. Unexpected
 * failures are logged with an identifier that also travels in the response, so
 * they can be investigated without exposing the detail to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Application exceptions: the type and status come from the exception itself. */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(
            ApplicationException exception, HttpServletRequest request) {

        ApiErrorResponse body = ApiErrorResponse.of(
                exception.type(), exception.status(), exception.getMessage(), request.getRequestURI());

        return ResponseEntity.status(exception.status()).body(body);
    }

    /** Authorisation denied by method security. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {

        // The exception message is not logged: it can quote the authorisation
        // expression, which is internal detail.
        log.info("Acesso negado em {}", request.getRequestURI());

        ApiErrorResponse body = ApiErrorResponse.of(
                ApiErrorType.FORBIDDEN,
                HttpStatus.FORBIDDEN.value(),
                "You do not have permission to perform this operation",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * A database constraint was violated.
     *
     * <p>The use cases check the rules before writing, but two concurrent
     * requests can pass the check together; the database is the last lock, and
     * that race must reach the client as a conflict rather than an unexpected
     * failure. The database's message is not passed on: it names tables and
     * constraints.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {

        log.warn("Violacao de integridade em {}", request.getRequestURI());

        ApiErrorResponse body = ApiErrorResponse.of(
                ApiErrorType.CONFLICT,
                HttpStatus.CONFLICT.value(),
                "The operation conflicts with the current state of the data",
                request.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** DTO validation: one item per invalid field, as in the example in doc 05 §1. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ApiErrorResponse.FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldErrorDetail(
                        error.getField(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();

        ApiErrorResponse body = ApiErrorResponse.withFieldErrors(
                ApiErrorType.VALIDATION_ERROR,
                HttpStatus.BAD_REQUEST.value(),
                "One or more fields are invalid",
                requestUri(request),
                fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Converts every exception Spring MVC handles into the API's format.
     *
     * <p>The original {@code body} is discarded on purpose: the default
     * {@code ProblemDetail} carries text such as "No static resource ..."
     * which exposes implementation detail.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        ApiErrorType type = toErrorType(status);

        ApiErrorResponse response = ApiErrorResponse.of(
                type, status.value(), type.title(), requestUri(request));

        return ResponseEntity.status(status).headers(headers).body(response);
    }

    /** A safety net: nothing should escape without the standard format. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        String incidentId = UUID.randomUUID().toString();

        // The detail stays in the server log, not in the response.
        log.error("Falha inesperada [incidente={}] em {}", incidentId, request.getRequestURI(), exception);

        ApiErrorResponse body = ApiErrorResponse.of(
                ApiErrorType.UNEXPECTED_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Unexpected error. Reference: " + incidentId,
                request.getRequestURI());

        return ResponseEntity.internalServerError().body(body);
    }

    private static ApiErrorType toErrorType(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> ApiErrorType.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ApiErrorType.METHOD_NOT_ALLOWED;
            case BAD_REQUEST -> ApiErrorType.MALFORMED_REQUEST;
            case FORBIDDEN -> ApiErrorType.FORBIDDEN;
            case UNAUTHORIZED -> ApiErrorType.AUTHENTICATION_REQUIRED;
            case CONFLICT -> ApiErrorType.CONFLICT;
            case TOO_MANY_REQUESTS -> ApiErrorType.TOO_MANY_REQUESTS;
            // CONTENT_TOO_LARGE is the current name for 413; PAYLOAD_TOO_LARGE
            // was deprecated in Spring 7 to follow RFC 9110.
            case CONTENT_TOO_LARGE -> ApiErrorType.PAYLOAD_TOO_LARGE;
            default -> ApiErrorType.UNEXPECTED_ERROR;
        };
    }

    private static String requestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false).replaceFirst("^uri=", "");
    }
}
