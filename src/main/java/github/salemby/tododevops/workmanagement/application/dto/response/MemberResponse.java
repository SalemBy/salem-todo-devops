package github.salemby.tododevops.workmanagement.application.dto.response;

import github.salemby.tododevops.workmanagement.domain.ProjectRole;

import java.time.Instant;

/** A project member. Display formatting of name and email is left to clients. */
public record MemberResponse(Long accountId, String displayName, String email, ProjectRole role,
                             Instant joinedAt, Instant updatedAt) {}
