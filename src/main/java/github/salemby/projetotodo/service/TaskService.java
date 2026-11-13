package github.salemby.projetotodo.service;

import github.salemby.projetotodo.dto.request.CreateTaskRequest;
import github.salemby.projetotodo.dto.request.UpdateTaskRequest;
import github.salemby.projetotodo.dto.response.TaskResponse;
import github.salemby.projetotodo.entity.Task;
import github.salemby.projetotodo.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
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
    public TaskResponse create(CreateTaskRequest request) {
        Task task = new Task();
        task.setTitle(request.title());
        task.setDescription(request.description());
        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
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
