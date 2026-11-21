package github.salemby.tododevops.workmanagement.domain;

import java.util.EnumSet;
import java.util.Set;

import static github.salemby.tododevops.workmanagement.domain.ProjectPermission.*;

/**
 * A member's role in one project, defined as a fixed set of permissions and declared by rank.
 * Custom roles (#42) will store this mapping per project instead.
 */
public enum ProjectRole {
    OWNER(EnumSet.allOf(ProjectPermission.class)),
    /** Runs the project's work and team; editing or archiving the project and managing owners and managers stay with OWNERs (#69). */
    MANAGER(EnumSet.of(PROJECT_READ, TASK_WRITE, LABEL_WRITE, COMMENT_WRITE, COMMENT_MODERATE, MEMBERS_MANAGE)),
    MEMBER(EnumSet.of(PROJECT_READ, TASK_WRITE, LABEL_WRITE, COMMENT_WRITE)),
    VIEWER(EnumSet.of(PROJECT_READ));

    private final Set<ProjectPermission> permissions;

    ProjectRole(Set<ProjectPermission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public boolean allows(ProjectPermission permission) {
        return permissions.contains(permission);
    }

    /** Roles that manage members (OWNER, MANAGER); only PRIVILEGED_MEMBERS_MANAGE may add, change or remove them. */
    public boolean privileged() {
        return allows(MEMBERS_MANAGE);
    }
}
