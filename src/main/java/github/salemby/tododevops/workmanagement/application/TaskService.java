package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.workmanagement.application.dto.request.CreateTaskRequest;
import github.salemby.tododevops.workmanagement.application.dto.request.UpdateTaskRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.TaskResponse;
import github.salemby.tododevops.workmanagement.domain.Task;
import github.salemby.tododevops.workmanagement.domain.Label;
import github.salemby.tododevops.workmanagement.application.dto.response.LabelResponse;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.LabelRepository;
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
    private final LabelRepository labels;
    private final github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository projects;

    public TaskService(TaskRepository taskRepository, github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository projects, LabelRepository labels) {
        this.taskRepository = taskRepository;
        this.projects = projects;
        this.labels = labels;
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
        applyMetadata(task, request);
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
        applyMetadata(task, request);
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

    private void applyMetadata(Task task, github.salemby.tododevops.workmanagement.application.dto.request.TaskMetadataRequest request) {
        if (request.type() != null) task.setType(request.type());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.dueDatePresent()) task.setDueDate(request.dueDate());
        replaceLabels(task, request.labelIds());
    }

    private void replaceLabels(Task task, java.util.List<Long> ids) {
        if (ids == null) return;
        var selected = labels.findAllByProjectIdAndIdIn(task.getProject().getId(), ids);
        if (selected.size() != ids.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found");
        var existing = task.getLabels().stream().map(Label::getId).collect(java.util.stream.Collectors.toSet());
        for (Label label : selected) {
            if (!label.isActive() && !existing.contains(label.getId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Label is archived");
        }
        task.replaceLabels(selected);
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> findLabels(Long projectId, Long id) {
        return findById(projectId, id).labels();
    }

    @Transactional
    public void changeLabel(Long projectId, Long id, Long labelId, boolean attach) {
        requireProject(projectId, true);
        Task task = taskRepository.findByIdAndProjectIdAndActiveTrue(id, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        Label label = labels.findByIdAndProjectId(labelId, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found"));
        if (attach) {
            if (!label.isActive() && !task.getLabels().contains(label))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Label is archived");
            task.getLabels().add(label);
        } else {
            task.getLabels().remove(label);
        }
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getTitle(), task.getDescription(),
            task.getStatus(), task.getCreatedAt(), task.getUpdatedAt(), task.isActive(),
            task.getType(), task.getPriority(), task.getDueDate(),
            task.getLabels().stream().sorted(java.util.Comparator.comparing(Label::getId)).map(LabelResponse::from).toList());
    }

}
