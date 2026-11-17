package github.salemby.tododevops.workmanagement.application.dto.request;

import github.salemby.tododevops.shared.validation.NotBlankText;
import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.Size;

public class CreateTaskRequest extends TaskMetadataRequest {
    @NotBlankText(message = "Title is required")
    @Size(max = 200, message = "Title must contain at most 200 characters")
    @NoNulText
    @com.fasterxml.jackson.annotation.JsonProperty private String title;
    @Size(max = 2000, message = "Description must contain at most 2000 characters")
    @NoNulText
    @com.fasterxml.jackson.annotation.JsonProperty private String description;

    public CreateTaskRequest() { }
    public CreateTaskRequest(String title, String description) { this.title=title; this.description=description; }
    public String title() { return title; }
    public String description() { return description; }
}
