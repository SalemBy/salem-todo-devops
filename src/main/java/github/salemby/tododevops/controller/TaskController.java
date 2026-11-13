package github.salemby.tododevops.controller;

import github.salemby.tododevops.dto.request.CreateTaskRequest;
import github.salemby.tododevops.dto.request.UpdateTaskRequest;
import github.salemby.tododevops.dto.response.TaskResponse;
import github.salemby.tododevops.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = taskService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(task.id()).toUri();
        return ResponseEntity.created(location).body(task);
    }
    @GetMapping
    public ResponseEntity<List<TaskResponse>> findAll(@RequestParam(defaultValue = "true") String active) {
        return ResponseEntity.ok(taskService.findAll(active));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(taskService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
