package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.workmanagement.application.dto.request.LabelRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.LabelResponse;
import github.salemby.tododevops.workmanagement.domain.Label;
import github.salemby.tododevops.workmanagement.domain.Project;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.LabelRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@Validated
public class LabelService {
    private final ProjectRepository projects;
    private final LabelRepository labels;

    public LabelService(ProjectRepository projects, LabelRepository labels) {
        this.projects = projects;
        this.labels = labels;
    }

    @Transactional
    public LabelResponse create(Long projectId, @NotNull @Valid LabelRequest request) {
        Project project = requireProject(projectId, true);
        String name = request.name().strip();
        requireUniqueName(projectId, name, null);
        return LabelResponse.from(labels.saveAndFlush(new Label(project, name)));
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> list(Long projectId, String active) {
        requireProject(projectId, false);
        var found = switch (active) {
            case "true" -> labels.findAllByProjectIdAndActiveOrderById(projectId, true);
            case "false" -> labels.findAllByProjectIdAndActiveOrderById(projectId, false);
            case "all" -> labels.findAllByProjectIdOrderById(projectId);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active must be true, false, or all");
        };
        return found.stream().map(LabelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LabelResponse read(Long projectId, Long id) {
        requireProject(projectId, false);
        return LabelResponse.from(requireLabel(projectId, id));
    }

    @Transactional
    public LabelResponse update(Long projectId, Long id, @NotNull @Valid LabelRequest request) {
        requireProject(projectId, true);
        Label label = requireLabel(projectId, id);
        if (!label.isActive()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found");
        String name = request.name().strip();
        requireUniqueName(projectId, name, id);
        label.rename(name);
        return LabelResponse.from(labels.saveAndFlush(label));
    }

    @Transactional
    public void archive(Long projectId, Long id) {
        requireProject(projectId, true);
        requireLabel(projectId, id).archive();
    }

    private void requireUniqueName(Long projectId, String name, Long excluded) {
        // Every label writer holds the project lock; PostgreSQL lower() matches the unique index.
        if (labels.nameExists(projectId, name, excluded))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Label name already exists");
    }

    private Label requireLabel(Long projectId, Long id) {
        return labels.findByIdAndProjectId(id, projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found"));
    }

    private Project requireProject(Long id, boolean writing) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project ID must be positive");
        var project = (writing ? projects.findLockedById(id) : projects.findById(id))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (writing && !project.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is archived");
        return project;
    }
}
