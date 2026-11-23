package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.dto.response.ActivityResponse;
import github.salemby.tododevops.workmanagement.application.dto.response.PageResponse;
import github.salemby.tododevops.workmanagement.domain.ActivityEvent;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ActivityEventRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.TaskRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads activity history (#12): any member, newest first, archived projects and tasks included. */
@Service
@Validated
public class ActivityService {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt", "id");

    private final ProjectAccess access;
    private final ActivityEventRepository events;
    private final TaskRepository tasks;

    public ActivityService(ProjectAccess access, ActivityEventRepository events, TaskRepository tasks) {
        this.access = access;
        this.events = events;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> projectFeed(@NotNull CurrentAccount account, Long projectId, int page, int size) {
        Paging.require(page, size);
        access.read(projectId, account);
        return toPage(events.findByProjectId(projectId, PageRequest.of(page, size, NEWEST_FIRST)), page, size);
    }

    /** The task's own events and those of its comments; archived tasks are found too. */
    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> taskFeed(@NotNull CurrentAccount account, Long projectId, Long taskId,
                                                   int page, int size) {
        Paging.require(page, size);
        access.read(projectId, account);
        tasks.findByIdAndProjectId(taskId, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        return toPage(events.findByProjectIdAndTaskId(projectId, taskId, PageRequest.of(page, size, NEWEST_FIRST)), page, size);
    }

    /** Pages the events, then loads their changes in one query and keeps the page's order. */
    private PageResponse<ActivityResponse> toPage(Page<ActivityEvent> found, int page, int size) {
        var ids = found.getContent().stream().map(ActivityEvent::getId).toList();
        Map<Long, ActivityEvent> loaded = ids.isEmpty() ? Map.of()
            : events.findAllByIdIn(ids).stream().collect(Collectors.toMap(ActivityEvent::getId, Function.identity()));
        return new PageResponse<>(ids.stream().map(loaded::get).map(ActivityResponse::from).toList(),
            page, size, found.getTotalElements(), found.getTotalPages());
    }
}
