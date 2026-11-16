package github.salemby.tododevops.workmanagement.application.dto.request;

import github.salemby.tododevops.shared.validation.NotBlankText;
import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
    @NotBlankText(message = "Name is required")
    @Size(max = 200, message = "Name must contain at most 200 characters")
    @NoNulText
    String name,
    @Size(max = 2000, message = "Description must contain at most 2000 characters")
    @NoNulText
    String description
) {
}
