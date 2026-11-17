package github.salemby.tododevops.workmanagement.application.dto.request;

import github.salemby.tododevops.workmanagement.domain.TaskStatus;
import github.salemby.tododevops.shared.validation.NotBlankText;
import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateTaskRequest extends TaskMetadataRequest {
    @NotBlankText(message = "Title is required")
    @Size(max = 200, message = "Title must contain at most 200 characters")
    @NoNulText
    @com.fasterxml.jackson.annotation.JsonProperty private String title;
    @Size(max = 2000, message = "Description must contain at most 2000 characters")
    @NoNulText
    @com.fasterxml.jackson.annotation.JsonProperty private String description;
    @NotNull(message = "Status is required")
    @com.fasterxml.jackson.annotation.JsonProperty private TaskStatus status;

    public UpdateTaskRequest() { }
    public UpdateTaskRequest(String title, String description, TaskStatus status) { this.title=title; this.description=description; this.status=status; }
    public String title() { return title; }
    public String description() { return description; }
    public TaskStatus status() { return status; }
}
