package github.salemby.tododevops.workmanagement.application.dto.response;

import github.salemby.tododevops.workmanagement.domain.ActivityAction;
import github.salemby.tododevops.workmanagement.domain.ActivityEvent;
import github.salemby.tododevops.workmanagement.domain.ResourceType;

import java.time.Instant;
import java.util.List;

public record ActivityResponse(Long id, Long projectId, Long taskId, Long actorId, ResourceType resourceType,
                               Long resourceId, ActivityAction action, Instant occurredAt, List<ChangeResponse> changes) {
    public static ActivityResponse from(ActivityEvent event) {
        return new ActivityResponse(event.getId(), event.getProjectId(), event.getTaskId(), event.getActorAccountId(),
            event.getResourceType(), event.getResourceId(), event.getAction(), event.getOccurredAt(),
            event.getChanges().stream().map(ChangeResponse::from).toList());
    }
}
