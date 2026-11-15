package github.salemby.tododevops.workmanagement.application.dto.response;

import github.salemby.tododevops.workmanagement.domain.TaskStatus;
import java.time.Instant;

public record TaskResponse(Long id, Long projectId, String title, String description,
                           TaskStatus status, Instant createdAt, Instant updatedAt, boolean active) {
}
