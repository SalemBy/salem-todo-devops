package github.salemby.tododevops.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Test-only task counts and cleanup by project, straight from the table (task_labels rows cascade). */
public final class TaskRows {
    private TaskRows() {}

    public static int count(JdbcTemplate jdbc, Long projectId) {
        return jdbc.queryForObject("select count(*) from tasks where project_id = ?", Integer.class, projectId);
    }

    public static void delete(JdbcTemplate jdbc, Long projectId) {
        jdbc.update("delete from tasks where project_id = ?", projectId);
    }
}
