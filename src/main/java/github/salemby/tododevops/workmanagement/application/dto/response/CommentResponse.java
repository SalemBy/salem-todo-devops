package github.salemby.tododevops.workmanagement.application.dto.response;

import github.salemby.tododevops.workmanagement.domain.TaskComment;

import java.time.Instant;

public record CommentResponse(Long id, Long taskId, Long authorId, String body, Instant createdAt, Instant updatedAt) {
    public static CommentResponse from(TaskComment comment) {
        return new CommentResponse(comment.getId(), comment.getTask().getId(), comment.getAuthorAccountId(),
            comment.getBody(), comment.getCreatedAt(), comment.getUpdatedAt());
    }
}
