package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.dto.request.CommentRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.CommentResponse;
import github.salemby.tododevops.workmanagement.application.dto.response.PageResponse;
import github.salemby.tododevops.workmanagement.domain.ProjectPermission;
import github.salemby.tododevops.workmanagement.domain.Task;
import github.salemby.tododevops.workmanagement.domain.TaskComment;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.TaskCommentRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.TaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

/**
 * Task comments (#11). Reads need PROJECT_READ and work on archived tasks and projects; writes lock the
 * project, need an active project and task, and keep the author and creation time unchanged.
 */
@Service
@Validated
public class TaskCommentService {
    static final String NOT_AUTHOR = "Only the author can edit this comment";

    private final ProjectAccess access;
    private final TaskRepository tasks;
    private final TaskCommentRepository comments;

    public TaskCommentService(ProjectAccess access, TaskRepository tasks, TaskCommentRepository comments) {
        this.access = access;
        this.tasks = tasks;
        this.comments = comments;
    }

    /** Active comments, oldest first. */
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(@NotNull CurrentAccount account, Long projectId, Long taskId, int page, int size) {
        Paging.require(page, size);
        access.read(projectId, account);
        requireTask(projectId, taskId);
        var found = comments.findByTaskIdAndActiveTrue(taskId, PageRequest.of(page, size, Sort.by("id")));
        return new PageResponse<>(found.getContent().stream().map(CommentResponse::from).toList(),
            page, size, found.getTotalElements(), found.getTotalPages());
    }

    @Transactional
    public CommentResponse create(@NotNull CurrentAccount account, Long projectId, Long taskId,
                                  @NotNull @Valid CommentRequest request) {
        access.lockActive(projectId, account, ProjectPermission.COMMENT_WRITE);
        Task task = requireActiveTask(projectId, taskId);
        return CommentResponse.from(comments.saveAndFlush(new TaskComment(task, account.id(), request.body())));
    }

    @Transactional(readOnly = true)
    public CommentResponse read(@NotNull CurrentAccount account, Long projectId, Long taskId, Long commentId) {
        access.read(projectId, account);
        requireTask(projectId, taskId);
        return CommentResponse.from(requireComment(taskId, commentId));
    }

    /** Only the author edits, and only while their role still allows writing comments. */
    @Transactional
    public CommentResponse edit(@NotNull CurrentAccount account, Long projectId, Long taskId, Long commentId,
                                @NotNull @Valid CommentRequest request) {
        access.lockActive(projectId, account, ProjectPermission.COMMENT_WRITE);
        requireActiveTask(projectId, taskId);
        TaskComment comment = requireComment(taskId, commentId);
        if (!comment.getAuthorAccountId().equals(account.id()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_AUTHOR);
        if (!comment.getBody().equals(request.body())) {
            comment.edit(request.body());
            comments.saveAndFlush(comment);
        }
        return CommentResponse.from(comment);
    }

    private void requireTask(Long projectId, Long taskId) {
        tasks.findByIdAndProjectId(taskId, projectId).orElseThrow(TaskCommentService::taskNotFound);
    }

    private Task requireActiveTask(Long projectId, Long taskId) {
        return tasks.findByIdAndProjectIdAndActiveTrue(taskId, projectId).orElseThrow(TaskCommentService::taskNotFound);
    }

    private TaskComment requireComment(Long taskId, Long commentId) {
        return comments.findByIdAndTaskIdAndActiveTrue(commentId, taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
    }

    private static ResponseStatusException taskNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
    }
}
