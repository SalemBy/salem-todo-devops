package github.salemby.tododevops.administration.application.dto;

import github.salemby.tododevops.identity.application.dto.AccountResponse;

import java.util.List;

/** The deactivated account, how many task assignments were cleared, and the projects it left with no active owner. */
public record DeactivationResponse(AccountResponse account, int unassignedTasks, List<Long> ownerlessProjectIds) {}
