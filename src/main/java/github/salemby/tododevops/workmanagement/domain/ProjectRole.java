package github.salemby.tododevops.workmanagement.domain;

import java.util.EnumSet;
import java.util.Set;

import static github.salemby.tododevops.workmanagement.domain.ProjectPermission.*;

/**
 * A member's role in one project, defined as a fixed set of permissions.
 * Custom roles (#42) will store this mapping per project instead.
 */
public enum ProjectRole {
    OWNER(EnumSet.allOf(ProjectPermission.class)),
    MEMBER(EnumSet.of(PROJECT_READ, TASK_WRITE, LABEL_WRITE, COMMENT_WRITE)),
    VIEWER(EnumSet.of(PROJECT_READ));

    private final Set<ProjectPermission> permissions;

    ProjectRole(Set<ProjectPermission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public boolean allows(ProjectPermission permission) {
        return permissions.contains(permission);
    }
}
