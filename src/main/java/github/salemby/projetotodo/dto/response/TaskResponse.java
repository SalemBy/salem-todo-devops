package github.salemby.projetotodo.dto.response;

import github.salemby.projetotodo.enums.TaskStatus;
import java.time.Instant;

public record TaskResponse(Long id, String title, String description,
                           TaskStatus status, Instant createdAt, Instant updatedAt, boolean active) {
}
