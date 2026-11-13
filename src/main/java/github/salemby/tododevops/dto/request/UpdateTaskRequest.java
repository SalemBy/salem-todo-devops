package github.salemby.tododevops.dto.request;

import github.salemby.tododevops.enums.TaskStatus;
import github.salemby.tododevops.validation.NotBlankText;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTaskRequest(
    @NotBlankText(message = "Title is required")
    @Size(max = 200, message = "Title must contain at most 200 characters")
    String title,
    @Size(max = 2000, message = "Description must contain at most 2000 characters")
    String description,
    @NotNull(message = "Status is required")
    TaskStatus status
) {
}
