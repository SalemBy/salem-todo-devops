package github.salemby.tododevops.workmanagement.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import github.salemby.tododevops.shared.validation.NotBlankText;
import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
    @NotBlankText(message = "Name is required")
    @Size(max = 200, message = "Name must contain at most 200 characters")
    @NoNulText
    String name,
    @NotBlank(message = "Key is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$",
        message = "Key must contain 2 to 10 uppercase letters or digits and start with a letter")
    String key,
    @Size(max = 2000, message = "Description must contain at most 2000 characters")
    @NoNulText
    String description
) {
}
