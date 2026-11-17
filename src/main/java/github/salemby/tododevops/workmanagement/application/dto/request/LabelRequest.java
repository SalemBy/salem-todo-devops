package github.salemby.tododevops.workmanagement.application.dto.request;

import github.salemby.tododevops.shared.validation.NoNulText;
import github.salemby.tododevops.shared.validation.NotBlankText;
import jakarta.validation.constraints.Size;

public record LabelRequest(
    @NotBlankText(message = "Label name is required")
    @NoNulText
    @Size(max = 50, message = "Label name must contain at most 50 characters")
    String name
) {}
