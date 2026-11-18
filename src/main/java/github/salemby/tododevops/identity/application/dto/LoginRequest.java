package github.salemby.tododevops.identity.application.dto;

import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record LoginRequest(
    @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[\\x21-\\x7E]+") String email,
    @NotNull @NoNulText @Size(min = 1, max = 256) String password
) {
    public LoginRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
