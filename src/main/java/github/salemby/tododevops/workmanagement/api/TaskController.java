package github.salemby.tododevops.workmanagement.api;

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
    public ResponseEntity<TaskResponse> create(@PathVariable Long projectId, @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = taskService.create(projectId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(task.id()).toUri();
        return ResponseEntity.created(location).body(task);
    }
    @GetMapping
    public ResponseEntity<List<TaskResponse>> findAll(@RequestParam(defaultValue = "true") String active, @PathVariable Long projectId) {
        return ResponseEntity.ok(taskService.findAllByProject(projectId, active));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(@PathVariable Long projectId, @PathVariable Long id) {
        return ResponseEntity.ok(taskService.findById(projectId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long projectId, @PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(taskService.update(projectId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long id) {
        taskService.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }
}
