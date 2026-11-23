package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.workmanagement.domain.ActivityAction;
import github.salemby.tododevops.workmanagement.domain.ActivityEvent;
import github.salemby.tododevops.workmanagement.domain.ResourceType;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ActivityEventRepository;
import org.springframework.stereotype.Component;

/**
 * Records activity history (#12). Services call it inside their transaction after the change is applied,
 * so a refused or rolled-back change leaves no event.
 */
@Component
public class ActivityLog {
    private final ActivityEventRepository events;

    ActivityLog(ActivityEventRepository events) {
        this.events = events;
    }

    /** Saves the event; an update that changed nothing is not recorded. */
    void record(long actorId, long projectId, Long taskId, ResourceType type, long resourceId,
                ActivityAction action, Changes changes) {
        if (action == ActivityAction.UPDATED && changes.isEmpty()) return;
        events.save(new ActivityEvent(projectId, taskId, actorId, type, resourceId, action, changes.list()));
    }
}
