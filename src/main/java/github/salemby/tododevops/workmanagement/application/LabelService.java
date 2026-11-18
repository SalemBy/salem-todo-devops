package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.workmanagement.application.dto.request.LabelRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.LabelResponse;
import github.salemby.tododevops.workmanagement.domain.Label;
import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.domain.Project;
import github.salemby.tododevops.workmanagement.domain.ProjectPermission;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.LabelRepository;
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
    private final ProjectAccess access;
    private final LabelRepository labels;

    public LabelService(ProjectAccess access, LabelRepository labels) {
        this.access = access;
        this.labels = labels;
    }

    @Transactional
    public LabelResponse create(@NotNull CurrentAccount account, Long projectId, @NotNull @Valid LabelRequest request) {
        Project project = access.lockActive(projectId, account, ProjectPermission.LABEL_WRITE);
        String name = request.name().strip();
        requireUniqueName(projectId, name, null);
        return LabelResponse.from(labels.saveAndFlush(new Label(project, name)));
    }

    @Transactional(readOnly = true)
    public List<LabelResponse> list(@NotNull CurrentAccount account, Long projectId, String active) {
        // Validate the filter before the access check: 400 precedes 404 and 403.
        if (!List.of("true", "false", "all").contains(active))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active must be true, false, or all");
        access.read(projectId, account);
        var found = switch (active) {
            case "true" -> labels.findAllByProjectIdAndActiveOrderById(projectId, true);
            case "false" -> labels.findAllByProjectIdAndActiveOrderById(projectId, false);
            default -> labels.findAllByProjectIdOrderById(projectId);
        };
        return found.stream().map(LabelResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LabelResponse read(@NotNull CurrentAccount account, Long projectId, Long id) {
        access.read(projectId, account);
        return LabelResponse.from(requireLabel(projectId, id));
    }

    @Transactional
    public LabelResponse update(@NotNull CurrentAccount account, Long projectId, Long id,
                                @NotNull @Valid LabelRequest request) {
        access.lockActive(projectId, account, ProjectPermission.LABEL_WRITE);
        Label label = requireLabel(projectId, id);
        if (!label.isActive()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found");
        String name = request.name().strip();
        requireUniqueName(projectId, name, id);
        label.rename(name);
        return LabelResponse.from(labels.saveAndFlush(label));
    }

    @Transactional
    public void archive(@NotNull CurrentAccount account, Long projectId, Long id) {
        access.lockActive(projectId, account, ProjectPermission.LABEL_WRITE);
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

}
