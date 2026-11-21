package github.salemby.tododevops.workmanagement.api;

import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.TaskCommentService;
import github.salemby.tododevops.workmanagement.application.dto.request.CommentRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.CommentResponse;
import github.salemby.tododevops.workmanagement.application.dto.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/projects/{projectId}/tasks/{taskId}/comments")
public class TaskCommentController {
    private final TaskCommentService comments;

    public TaskCommentController(TaskCommentService comments) {
        this.comments = comments;
    }

    @GetMapping
    public PageResponse<CommentResponse> list(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return comments.list(account, projectId, taskId, page, size);
    }

    @PostMapping
    public ResponseEntity<CommentResponse> create(CurrentAccount account, @PathVariable Long projectId,
            @PathVariable Long taskId, @Valid @RequestBody CommentRequest request) {
        CommentResponse comment = comments.create(account, projectId, taskId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(comment.id()).toUri();
        return ResponseEntity.created(location).body(comment);
    }

    @GetMapping("/{commentId}")
    public CommentResponse read(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long taskId,
            @PathVariable Long commentId) {
        return comments.read(account, projectId, taskId, commentId);
    }

    @PutMapping("/{commentId}")
    public CommentResponse edit(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long taskId,
            @PathVariable Long commentId, @Valid @RequestBody CommentRequest request) {
        return comments.edit(account, projectId, taskId, commentId, request);
    }
}
