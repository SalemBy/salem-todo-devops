package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.domain.Project;
import github.salemby.tododevops.workmanagement.domain.ProjectMembership;
import github.salemby.tododevops.workmanagement.domain.ProjectPermission;
import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectMembershipRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * The single place that loads a project for a caller and enforces membership permissions.
 * Error order: 400 invalid ID, 404 missing project or non-member, 403 role lacks the
 * permission. Callers apply resource-state checks (such as archived projects) afterwards.
 * Callers must run inside a transaction.
 */
@Component
public class ProjectAccess {
    static final String NOT_FOUND = "Project not found";
    static final String FORBIDDEN = "Your project role does not allow this action";

    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;

    public ProjectAccess(ProjectRepository projects, ProjectMembershipRepository memberships) {
        this.projects = projects;
        this.memberships = memberships;
    }

    /** A project the caller may act on, with the caller's role. */
    public record Granted(Project project, ProjectRole role) {}

    /** Loads the project without locking; for reads. */
    public Granted read(Long projectId, CurrentAccount account) {
        requireValidId(projectId);
        Project project = projects.findById(projectId).orElseThrow(ProjectAccess::notFound);
        return grant(project, account, ProjectPermission.PROJECT_READ);
    }

    /**
     * Locks the project row, then checks the caller's permission. Membership changes take the
     * same lock, so a write cannot race with removing the caller's access.
     */
    public Granted lock(Long projectId, CurrentAccount account, ProjectPermission permission) {
        requireValidId(projectId);
        Project project = projects.findLockedById(projectId).orElseThrow(ProjectAccess::notFound);
        return grant(project, account, permission);
    }

    /** Like {@link #lock}, and also rejects archived projects with 409. */
    public Project lockActive(Long projectId, CurrentAccount account, ProjectPermission permission) {
        Project project = lock(projectId, account, permission).project();
        if (!project.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Project is archived");
        return project;
    }

    private Granted grant(Project project, CurrentAccount account, ProjectPermission permission) {
        Objects.requireNonNull(account, "Account is required");
        // Non-members get the same 404 as a missing project, so existence is never revealed.
        ProjectRole role = memberships.findByProjectIdAndAccountId(project.getId(), account.id())
            .map(ProjectMembership::getRole).orElseThrow(ProjectAccess::notFound);
        if (!role.allows(permission)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN);
        return new Granted(project, role);
    }

    private static void requireValidId(Long projectId) {
        if (projectId == null || projectId <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project ID must be positive");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }
}
