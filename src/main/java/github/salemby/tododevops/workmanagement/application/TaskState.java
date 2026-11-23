package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.workmanagement.domain.Task;
import github.salemby.tododevops.workmanagement.domain.TaskPriority;
import github.salemby.tododevops.workmanagement.domain.TaskStatus;
import github.salemby.tododevops.workmanagement.domain.TaskType;

import java.time.LocalDate;

/** The recorded fields of a task at one moment, compared before and after a write (#12). */
record TaskState(String title, String description, TaskStatus status, TaskType type, TaskPriority priority,
                 LocalDate dueDate, String labelIds, Long assigneeId) {
    /** Before a task exists: every field is a change on creation. */
    static final TaskState NONE = new TaskState(null, null, null, null, null, null, null, null);

    static TaskState of(Task task) {
        return new TaskState(task.getTitle(), task.getDescription(), task.getStatus(), task.getType(), task.getPriority(),
            task.getDueDate(), Changes.labelIds(task.getLabels()), task.getAssigneeAccountId());
    }

    /** The fields that differ, in this record's order; the description text is never kept. */
    Changes diff(TaskState after) {
        return new Changes().value("title", title, after.title).text("description", description, after.description)
            .value("status", status, after.status).value("type", type, after.type)
            .value("priority", priority, after.priority).value("dueDate", dueDate, after.dueDate)
            .value("labelIds", labelIds, after.labelIds).value("assigneeId", assigneeId, after.assigneeId);
    }
}
