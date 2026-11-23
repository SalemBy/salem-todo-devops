package github.salemby.tododevops.workmanagement.infrastructure.persistence;

/** A task an account is assigned to, read before a bulk unassign so each change can be recorded (#12). */
public record AssignedTask(Long id, Long projectId) {}
