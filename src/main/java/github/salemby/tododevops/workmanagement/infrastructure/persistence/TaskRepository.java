package github.salemby.tododevops.workmanagement.infrastructure.persistence;

import github.salemby.tododevops.workmanagement.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Task> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "labels")
    List<Task> findAllByProjectIdAndIdIn(Long projectId, List<Long> ids);


    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "labels")
    Optional<Task> findByIdAndProjectId(Long id, Long projectId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "labels")
    Optional<Task> findByIdAndProjectIdAndActiveTrue(Long id, Long projectId);

    /**
     * Locks and returns the tasks {@link #clearAssignee} will change, archived ones included. FOR UPDATE makes
     * PostgreSQL re-check rows a concurrent write changed, so the result is exactly the rows then cleared (#12).
     */
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = """
        SELECT id, project_id AS projectId FROM tasks
        WHERE project_id = :projectId AND assignee_account_id = :accountId ORDER BY id FOR UPDATE""")
    List<AssignedTask> findAssigned(@org.springframework.data.repository.query.Param("projectId") Long projectId,
                                    @org.springframework.data.repository.query.Param("accountId") Long accountId);

    /** Locks and returns the tasks {@link #clearAssigneeEverywhere} will change, like {@link #findAssigned}. */
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = """
        SELECT id, project_id AS projectId FROM tasks WHERE assignee_account_id = :accountId ORDER BY id FOR UPDATE""")
    List<AssignedTask> findAssignedEverywhere(@org.springframework.data.repository.query.Param("accountId") Long accountId);

    /** Unassigns the account from every task in the project, archived ones included; returns how many changed. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
        update Task t set t.assigneeAccountId = null, t.updatedAt = :now
        where t.project.id = :projectId and t.assigneeAccountId = :accountId""")
    int clearAssignee(@org.springframework.data.repository.query.Param("projectId") Long projectId,
                      @org.springframework.data.repository.query.Param("accountId") Long accountId,
                      @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    /** Unassigns the account from every task in every project, archived ones included; returns how many changed. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update Task t set t.assigneeAccountId = null, t.updatedAt = :now where t.assigneeAccountId = :accountId")
    int clearAssigneeEverywhere(@org.springframework.data.repository.query.Param("accountId") Long accountId,
                                @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
