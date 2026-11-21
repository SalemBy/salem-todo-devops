package github.salemby.tododevops.workmanagement.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/** A plain-text comment on a task. The author and creation time never change; deletion only hides it. */
@Entity
@Table(name = "task_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @Column(name = "author_account_id", nullable = false, updatable = false)
    private Long authorAccountId;

    @Column(nullable = false, length = 10000)
    private String body;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public TaskComment(Task task, Long authorAccountId, String body) {
        this.task = Objects.requireNonNull(task);
        this.authorAccountId = Objects.requireNonNull(authorAccountId);
        this.body = Objects.requireNonNull(body);
    }

    public void edit(String body) {
        this.body = Objects.requireNonNull(body);
    }

    public void delete() {
        active = false;
    }

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
