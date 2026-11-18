package github.salemby.tododevops.workmanagement.application.dto.request;

import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record ChangeMemberRoleRequest(@NotNull(message = "Role is required") ProjectRole role) {}
