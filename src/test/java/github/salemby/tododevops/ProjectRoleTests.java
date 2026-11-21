package github.salemby.tododevops;

import github.salemby.tododevops.workmanagement.domain.ProjectPermission;
import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static github.salemby.tododevops.workmanagement.domain.ProjectPermission.*;
import static github.salemby.tododevops.workmanagement.domain.ProjectRole.*;
import static org.junit.jupiter.api.Assertions.*;

/** The fixed role-to-permission table (#9, #69): each row exactly, so a stray grant fails here. */
class ProjectRoleTests {
    @Test
    void eachRoleHasExactlyItsPermissions() {
        var expected = Map.of(
            OWNER, EnumSet.allOf(ProjectPermission.class),
            MANAGER, EnumSet.of(PROJECT_READ, TASK_WRITE, LABEL_WRITE, COMMENT_WRITE, COMMENT_MODERATE, MEMBERS_MANAGE),
            MEMBER, EnumSet.of(PROJECT_READ, TASK_WRITE, LABEL_WRITE, COMMENT_WRITE),
            VIEWER, EnumSet.of(PROJECT_READ));
        for (var role : ProjectRole.values()) assertEquals(expected.get(role), granted(role), role.name());
    }

    @Test
    void onlyOwnersAndManagersArePrivileged() {
        assertTrue(OWNER.privileged());
        assertTrue(MANAGER.privileged());
        assertFalse(MEMBER.privileged());
        assertFalse(VIEWER.privileged());
    }

    @Test
    void rolesAreDeclaredByRank() {
        assertEquals(List.of(OWNER, MANAGER, MEMBER, VIEWER), List.of(ProjectRole.values()));
    }

    private static Set<ProjectPermission> granted(ProjectRole role) {
        return Arrays.stream(ProjectPermission.values()).filter(role::allows)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(ProjectPermission.class)));
    }
}
