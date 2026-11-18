package github.salemby.tododevops.workmanagement.application.dto.response;

import github.salemby.tododevops.workmanagement.domain.ProjectRole;

import java.time.Instant;

/** {@code role} is the caller's role in the project; non-members never receive a project. */
public record ProjectResponse(Long id, String name, String key, String description,
                              boolean active, Instant createdAt, Instant updatedAt,
                              Long createdByAccountId, ProjectRole role) {
}
