package com.jhcomp.prospect.shared.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The API's single error format, exactly as specified in doc 05 §1.
 *
 * <p>Spring's {@code ProblemDetail} is not used here because the contract's
 * {@code type} field is a stable code such as {@code VALIDATION_ERROR}, while
 * {@code ProblemDetail.type} is a {@code URI}. Keeping the documented contract
 * matters more than reusing the framework's class.
 *
 * <p>It never carries a stack trace or an internal message (RNF-003).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        List<FieldErrorDetail> errors) {

    public record FieldErrorDetail(String field, String message) {}

    public static ApiErrorResponse of(ApiErrorType type, int status, String detail, String instance) {
        return new ApiErrorResponse(type.name(), type.title(), status, detail, instance, null);
    }

    public static ApiErrorResponse withFieldErrors(
            ApiErrorType type, int status, String detail, String instance, List<FieldErrorDetail> errors) {
        return new ApiErrorResponse(type.name(), type.title(), status, detail, instance, List.copyOf(errors));
    }
}
