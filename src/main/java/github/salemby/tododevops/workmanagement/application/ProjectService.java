package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.workmanagement.application.dto.request.CreateProjectRequest;
import github.salemby.tododevops.workmanagement.application.dto.request.UpdateProjectRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.ProjectResponse;
import github.salemby.tododevops.workmanagement.domain.Project;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@Validated
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectResponse create(@NotNull @Valid CreateProjectRequest request) {
        try {
            return toResponse(projectRepository.saveAndFlush(
                new Project(request.name(), request.key(), request.description())));
        } catch (DataIntegrityViolationException ex) {
            // Flush inside this transaction so concurrent duplicates also become 409.
            if (isDuplicateKey(ex)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Project key already exists", ex);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll(String active) {
        List<Project> projects = switch (active) {
            case "true" -> projectRepository.findAllByActive(true);
            case "false" -> projectRepository.findAllByActive(false);
            case "all" -> projectRepository.findAll();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Active must be true, false, or all");
        };
        return projects.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(Long id) {
        return toResponse(requireProject(id));
    }

    @Transactional
    public ProjectResponse update(Long id, @NotNull @Valid UpdateProjectRequest request) {
        Project project = projectRepository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        project.setName(request.name());
        project.setDescription(request.description());
        return toResponse(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public void delete(Long id) {
        projectRepository.findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")).archive();
    }

    private Project requireProject(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getKey(), project.getDescription(),
            project.isActive(), project.getCreatedAt(), project.getUpdatedAt());
    }

    private boolean isDuplicateKey(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.postgresql.util.PSQLException postgres
                    && "23505".equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null
                    && "uk_projects_key".equals(postgres.getServerErrorMessage().getConstraint())) {
                return true;
            }
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState()) && violation.getConstraintName() != null) {
                // H2 qualifies the constraint and appends backing-index details.
                String constraint = violation.getConstraintName().toLowerCase(Locale.ROOT).split(" ", 2)[0];
                return constraint.equals("uk_projects_key") || constraint.equals("public.uk_projects_key");
            }
        }
        return false;
    }
}
