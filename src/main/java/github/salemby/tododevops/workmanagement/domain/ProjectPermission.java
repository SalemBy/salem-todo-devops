package github.salemby.tododevops.workmanagement.domain;

/** What a project member may do. Endpoints check permissions, never role names. */
public enum ProjectPermission {
    /** Read the project, its tasks and labels, and its members. */
    PROJECT_READ,
    /** Create, update, or delete tasks; attach or detach task labels. */
    TASK_WRITE,
    /** Create, update, or delete labels. */
    LABEL_WRITE,
    /** Update or archive the project. */
    PROJECT_MANAGE,
    /** Add members, change roles, and remove other members. */
    MEMBERS_MANAGE
}
