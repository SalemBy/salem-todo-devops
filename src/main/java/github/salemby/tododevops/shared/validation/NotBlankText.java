package github.salemby.tododevops.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Required text must contain more than whitespace, Unicode separators, or format characters. */
@Documented
@NotBlank
@Pattern(regexp = "(?s).*[^\\p{javaWhitespace}\\p{Z}\\p{Cf}].*")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
public @interface NotBlankText {
    String message() default "Text is required";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
