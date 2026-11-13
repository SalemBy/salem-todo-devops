package github.salemby.tododevops.dto.request;

import github.salemby.tododevops.validation.NotBlankText;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
    @NotBlankText(message = "Name is required")
    @Size(max = 200, message = "Name must contain at most 200 characters")
    String name,
    @Size(max = 2000, message = "Description must contain at most 2000 characters")
    String description
) {
}
