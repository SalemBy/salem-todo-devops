package github.salemby.tododevops.support;

import github.salemby.tododevops.workmanagement.domain.Project;
import github.salemby.tododevops.workmanagement.domain.ProjectMembership;
import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectMembershipRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;

import java.util.Locale;
import java.util.UUID;

/** Fixture projects saved directly, owned by a given account the way the API would create them. */
public final class TestProjects {
    private TestProjects() {}

    public static Project owned(ProjectRepository projects, ProjectMembershipRepository memberships,
                                long ownerAccountId, String name, String key) {
        Project project = projects.saveAndFlush(new Project(name, key, null, ownerAccountId));
        memberships.saveAndFlush(new ProjectMembership(project, ownerAccountId, ProjectRole.OWNER));
        return project;
    }

    /** A unique valid project key: a letter followed by eight hex characters. */
    public static String uniqueKey(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
