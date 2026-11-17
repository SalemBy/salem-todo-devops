package github.salemby.tododevops.workmanagement.application.dto.response;

import github.salemby.tododevops.workmanagement.domain.Label;
import java.time.Instant;

public record LabelResponse(Long id, Long projectId, String name, boolean active,
                            Instant createdAt, Instant updatedAt) {
    public static LabelResponse from(Label label) {
        return new LabelResponse(label.getId(), label.getProject().getId(), label.getName(),
            label.isActive(), label.getCreatedAt(), label.getUpdatedAt());
    }
}
