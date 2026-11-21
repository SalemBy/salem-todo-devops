package github.salemby.tododevops.identity.application.dto;

import github.salemby.tododevops.shared.validation.Emails;
import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.*;

public record LoginRequest(
    @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[\\x21-\\x7E]+") String email,
    @NotNull @NoNulText @Size(min = 1, max = 256) String password
) {
    public LoginRequest {
        email = Emails.normalize(email);
    }
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
