package github.salemby.tododevops.workmanagement.application.dto.request;

import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import jakarta.validation.constraints.*;

import java.util.Locale;

/** Adds an existing active account by email. Email rules match account registration. */
public record AddMemberRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 254, message = "Email must contain at most 254 characters")
    @Pattern(regexp = "[\\x21-\\x7E]+", message = "Email must contain only visible ASCII characters")
    String email,
    @NotNull(message = "Role is required")
    ProjectRole role
) {
    public AddMemberRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }
}
