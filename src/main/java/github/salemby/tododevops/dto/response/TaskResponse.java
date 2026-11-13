package github.salemby.tododevops.dto.response;

import github.salemby.tododevops.enums.TaskStatus;
import java.time.Instant;

public record TaskResponse(Long id, String title, String description,
                           TaskStatus status, Instant createdAt, Instant updatedAt, boolean active) {
}
