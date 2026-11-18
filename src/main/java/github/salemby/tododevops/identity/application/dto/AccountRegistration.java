package github.salemby.tododevops.identity.application.dto;

import github.salemby.tododevops.shared.validation.NotBlankText;
import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record AccountRegistration(
    @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[\\x21-\\x7E]+") String email,
    @NotBlankText @NoNulText @Size(max = 100) String displayName,
    @NotNull @NoNulText String password
) {
    public AccountRegistration {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }

    @AssertTrue(message = "Password must contain 15 to 128 Unicode code points")
    public boolean isPasswordLengthValid() {
        return password != null && password.codePointCount(0, password.length()) >= 15
            && password.codePointCount(0, password.length()) <= 128;
    }

    @Override
    public String toString() { return "AccountRegistration[REDACTED]"; }
}
