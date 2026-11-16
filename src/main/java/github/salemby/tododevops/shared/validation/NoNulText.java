package github.salemby.tododevops.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** PostgreSQL text cannot persist NUL; null values remain valid for optional fields. */
@Documented
@Pattern(regexp = "[^\\x00]*")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface NoNulText {
    String message() default "Text must not contain NUL characters";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
