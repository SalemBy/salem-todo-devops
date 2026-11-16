package github.salemby.tododevops.workmanagement.application.dto.request;

import github.salemby.tododevops.shared.validation.NotBlankText;
import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
    @NotBlankText(message = "Title is required")
    @Size(max = 200, message = "Title must contain at most 200 characters")
    @NoNulText
    String title,
    @Size(max = 2000, message = "Description must contain at most 2000 characters")
    @NoNulText
    String description
) {
}
