package com.jhcomp.prospect.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * URL web: {@code http}, {@code https} ou sem esquema, com host valido.
 *
 * <p>Nulo e texto vazio sao aceitos ("sem valor"). Ver {@link WebUrlValidator}
 * para a razao de nao usar expressao regular.
 */
@Documented
@Constraint(validatedBy = WebUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface WebUrl {

    String message() default "must be an http or https URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
