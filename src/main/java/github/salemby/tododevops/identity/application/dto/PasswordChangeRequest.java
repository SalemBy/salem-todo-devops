package github.salemby.tododevops.identity.application.dto;

import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.*;

public record PasswordChangeRequest(
    @NotNull @NoNulText @Size(min = 1, max = 256) String currentPassword,
    @NotNull @NoNulText String newPassword
) {
    @AssertTrue(message = "New password must contain 15 to 128 Unicode code points")
    public boolean isPasswordLengthValid() {
        return newPassword != null && newPassword.codePointCount(0, newPassword.length()) >= 15
            && newPassword.codePointCount(0, newPassword.length()) <= 128;
    }
    @Override public String toString() { return "PasswordChangeRequest[REDACTED]"; }
}
