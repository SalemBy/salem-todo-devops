package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.workmanagement.application.dto.request.CreateTaskRequest;
import github.salemby.tododevops.workmanagement.application.dto.request.UpdateTaskRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.TaskResponse;
import github.salemby.tododevops.workmanagement.domain.Task;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.TaskRepository;
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
    private final github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository projects;

    public TaskService(TaskRepository taskRepository, github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository projects) {
        this.taskRepository = taskRepository;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findAllByProject(Long projectId, String active) {
        requireProject(projectId, false);
        List<Task> tasks = switch (active) {
            case "true" -> taskRepository.findAllByProjectIdAndActive(projectId, true);
            case "false" -> taskRepository.findAllByProjectIdAndActive(projectId, false);
            case "all" -> taskRepository.findAllByProjectId(projectId);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Active must be true, false, or all");
        };
        return tasks.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaskResponse create(Long projectId, @NotNull @Valid CreateTaskRequest request) {
        Task task = new Task(requireProject(projectId, true));
        task.setTitle(request.title());
        task.setDescription(request.description());
        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    @Transactional
    public TaskResponse update(Long projectId, Long id, @NotNull @Valid UpdateTaskRequest request) {
        requireProject(projectId, true);
        Task task = taskRepository.findByIdAndProjectIdAndActiveTrue(id, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        Task saved = taskRepository.saveAndFlush(task);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long projectId, Long id) {
        requireProject(projectId, true);
        Task task = taskRepository.findByIdAndProjectId(id, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        task.archive();
    }


    @Transactional(readOnly = true)
    public TaskResponse findById(Long projectId, Long id) {
        requireProject(projectId, false);
        return toResponse(taskRepository.findByIdAndProjectId(id, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found")));
    }

    private github.salemby.tododevops.workmanagement.domain.Project requireProject(Long id, boolean writing) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project ID must be positive");
        var project = (writing ? projects.findLockedById(id) : projects.findById(id))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (writing && !project.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is archived");
        return project;
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getTitle(), task.getDescription(),
            task.getStatus(), task.getCreatedAt(), task.getUpdatedAt(), task.isActive());
    }

}
