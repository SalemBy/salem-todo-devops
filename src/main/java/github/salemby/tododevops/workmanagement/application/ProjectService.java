package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.shared.persistence.UniqueConstraints;
import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.dto.request.CreateProjectRequest;
import github.salemby.tododevops.workmanagement.application.dto.request.UpdateProjectRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.ProjectResponse;
import github.salemby.tododevops.workmanagement.domain.Project;
import github.salemby.tododevops.workmanagement.domain.ProjectMembership;
import github.salemby.tododevops.workmanagement.domain.ProjectPermission;
import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectMembershipRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Validated
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final ProjectAccess access;

    public ProjectService(ProjectRepository projectRepository, ProjectMembershipRepository membershipRepository,
                          ProjectAccess access) {
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.access = access;
    }

    /** Creates the project and the creator's OWNER membership in one transaction. */
    @Transactional
    public ProjectResponse create(@NotNull CurrentAccount account, @NotNull @Valid CreateProjectRequest request) {
        try {
            Project project = projectRepository.saveAndFlush(
                new Project(request.name(), request.key(), request.description(), account.id()));
            membershipRepository.saveAndFlush(new ProjectMembership(project, account.id(), ProjectRole.OWNER));
            return toResponse(project, ProjectRole.OWNER);
        } catch (DataIntegrityViolationException ex) {
            // Flush inside this transaction so concurrent duplicates also become 409.
            if (UniqueConstraints.violated(ex, "uk_projects_key")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Project key already exists", ex);
            }
            throw ex;
        }
    }

    /** Only projects where the caller is a member, ordered by ID. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll(@NotNull CurrentAccount account, String active) {
        List<ProjectMembership> memberships = switch (active) {
            case "true" -> membershipRepository.findAllWithProjectByAccountIdAndActive(account.id(), true);
            case "false" -> membershipRepository.findAllWithProjectByAccountIdAndActive(account.id(), false);
            case "all" -> membershipRepository.findAllWithProjectByAccountId(account.id());
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Active must be true, false, or all");
        };
        return memberships.stream().map(m -> toResponse(m.getProject(), m.getRole())).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(@NotNull CurrentAccount account, Long id) {
        var granted = access.read(id, account);
        return toResponse(granted.project(), granted.role());
    }

    @Transactional
    public ProjectResponse update(@NotNull CurrentAccount account, Long id, @NotNull @Valid UpdateProjectRequest request) {
        // Serialize with archive and task writes; inspect activity only after acquiring the lock.
        var granted = access.lock(id, account, ProjectPermission.PROJECT_MANAGE);
        Project project = granted.project();
        if (!project.isActive()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        project.setName(request.name());
        project.setDescription(request.description());
        return toResponse(projectRepository.saveAndFlush(project), granted.role());
    }

    @Transactional
    public void delete(@NotNull CurrentAccount account, Long id) {
        access.lock(id, account, ProjectPermission.PROJECT_MANAGE).project().archive();
    }

    private ProjectResponse toResponse(Project project, ProjectRole role) {
        return new ProjectResponse(project.getId(), project.getName(), project.getKey(), project.getDescription(),
            project.isActive(), project.getCreatedAt(), project.getUpdatedAt(), project.getCreatedByAccountId(), role);
    }
}
