package com.jhcomp.prospect.shared.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Formato unico de erro da API, exatamente como especificado no doc 05 §1.
 *
 * <p>Nao se usa o {@code ProblemDetail} do Spring aqui porque o campo
 * {@code type} do contrato e um codigo estavel como {@code VALIDATION_ERROR},
 * enquanto {@code ProblemDetail.type} e um {@code URI}. Manter o contrato
 * documentado e mais importante que reaproveitar a classe do framework.
 *
 * <p>Nunca carrega stack trace nem mensagem interna (RNF-003).
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
