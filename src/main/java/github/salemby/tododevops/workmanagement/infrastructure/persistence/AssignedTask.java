package github.salemby.tododevops.workmanagement.infrastructure.persistence;

/** A task an account is assigned to, locked before a bulk unassign so each change can be recorded (#12). */
public interface AssignedTask {
    Long getId();

    Long getProjectId();
}
