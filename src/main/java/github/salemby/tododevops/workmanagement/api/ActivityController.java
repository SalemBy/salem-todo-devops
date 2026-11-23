package github.salemby.tododevops.workmanagement.api;

import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.ActivityService;
import github.salemby.tododevops.workmanagement.application.dto.response.ActivityResponse;
import github.salemby.tododevops.workmanagement.application.dto.response.PageResponse;
import org.springframework.web.bind.annotation.*;

/** Read-only activity history (#12): no endpoint changes or deletes an event. */
@RestController
@RequestMapping("/projects/{projectId}")
public class ActivityController {
    private final ActivityService activity;

    public ActivityController(ActivityService activity) {
        this.activity = activity;
    }

    @GetMapping("/activity")
    public PageResponse<ActivityResponse> project(CurrentAccount account, @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return activity.projectFeed(account, projectId, page, size);
    }

    @GetMapping("/tasks/{taskId}/activity")
    public PageResponse<ActivityResponse> task(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return activity.taskFeed(account, projectId, taskId, page, size);
    }
}
