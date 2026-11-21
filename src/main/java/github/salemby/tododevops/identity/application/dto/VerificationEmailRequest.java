package github.salemby.tododevops.identity.application.dto;

import github.salemby.tododevops.shared.validation.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The address to send a new verification email to (#71). */
public record VerificationEmailRequest(
    @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[\\x21-\\x7E]+") String email
) {
    public VerificationEmailRequest {
        email = Emails.normalize(email);
    }
}
