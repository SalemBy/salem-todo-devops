package github.salemby.tododevops.workmanagement.api;

import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.dto.response.PageResponse;
import github.salemby.tododevops.workmanagement.application.dto.request.TaskFilters;
import github.salemby.tododevops.workmanagement.application.dto.request.CreateTaskRequest;
import github.salemby.tododevops.workmanagement.application.dto.request.UpdateTaskRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.TaskResponse;
import github.salemby.tododevops.workmanagement.application.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(CurrentAccount account, @PathVariable Long projectId, @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = taskService.create(account, projectId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(task.id()).toUri();
        return ResponseEntity.created(location).body(task);
    }
    @GetMapping
    public ResponseEntity<PageResponse<TaskResponse>> findAll(CurrentAccount account, @RequestParam(defaultValue = "true") String active, @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status, @RequestParam(required = false) String type,
            @RequestParam(required = false) String priority, @RequestParam(required = false) String labelId,
            @RequestParam(required = false) String dueFrom, @RequestParam(required = false) String dueTo) {
        return ResponseEntity.ok(taskService.findAllByProject(account, projectId, active, page, size,
            TaskFilters.parse(status, type, priority, labelId, dueFrom, dueTo)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long id) {
        return ResponseEntity.ok(taskService.findById(account, projectId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(taskService.update(account, projectId, id, request));
    }

    @GetMapping("/{id}/labels")
    public List<github.salemby.tododevops.workmanagement.application.dto.response.LabelResponse> labels(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long id) {
        return taskService.findLabels(account, projectId, id);
    }

    @PutMapping("/{id}/labels/{labelId}")
    public ResponseEntity<Void> attachLabel(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long id, @PathVariable Long labelId) {
        taskService.changeLabel(account, projectId, id, labelId, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/labels/{labelId}")
    public ResponseEntity<Void> detachLabel(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long id, @PathVariable Long labelId) {
        taskService.changeLabel(account, projectId, id, labelId, false);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long id) {
        taskService.delete(account, projectId, id);
        return ResponseEntity.noContent().build();
    }
}
