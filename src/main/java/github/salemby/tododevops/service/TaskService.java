package github.salemby.tododevops.service;

import github.salemby.tododevops.dto.request.CreateTaskRequest;
import github.salemby.tododevops.dto.request.UpdateTaskRequest;
import github.salemby.tododevops.dto.response.TaskResponse;
import github.salemby.tododevops.entity.Task;
import github.salemby.tododevops.repository.TaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Validated
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findAll(String active) {
        List<Task> tasks = switch (active) {
            case "true" -> taskRepository.findAllByActive(true);
            case "false" -> taskRepository.findAllByActive(false);
            case "all" -> taskRepository.findAll();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Active must be true, false, or all");
        };
        return tasks.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaskResponse create(@NotNull @Valid CreateTaskRequest request) {
        Task task = new Task();
        task.setTitle(request.title());
        task.setDescription(request.description());
        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    @Transactional
    public TaskResponse update(Long id, @NotNull @Valid UpdateTaskRequest request) {
        Task task = taskRepository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        Task saved = taskRepository.saveAndFlush(task);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        task.setActive(false);
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
            task.getStatus(), task.getCreatedAt(), task.getUpdatedAt(), task.isActive());
    }

}
