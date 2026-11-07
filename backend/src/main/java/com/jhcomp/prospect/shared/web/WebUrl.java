package com.jhcomp.prospect.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A web URL: {@code http}, {@code https} or no scheme, with a valid host.
 *
 * <p>Null and the empty string are accepted ("no value"). See
 * {@link WebUrlValidator} for why no regular expression is used.
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
