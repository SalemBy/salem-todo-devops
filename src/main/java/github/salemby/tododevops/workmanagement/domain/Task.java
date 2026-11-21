package github.salemby.tododevops.workmanagement.domain;

import github.salemby.tododevops.workmanagement.domain.TaskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Task {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    public Task(Project project) {
        this.project = java.util.Objects.requireNonNull(project);
    }

    public void archive() {
        active = false;
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 200)
    private String title;

    @Setter
    @Column(length = 2000)
    private String description;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskType type = TaskType.TASK;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Setter
    private java.time.LocalDate dueDate;

    @ManyToMany
    @JoinTable(name = "task_labels", joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "label_id"))
    private java.util.Set<Label> labels = new java.util.HashSet<>();

    /** A project member who may write tasks; the database keeps it within the project's members. */
    @Column(name = "assignee_account_id")
    private Long assigneeAccountId;

    public void assign(Long accountId) {
        assigneeAccountId = java.util.Objects.requireNonNull(accountId);
    }

    public void unassign() {
        assigneeAccountId = null;
    }

    public void replaceLabels(java.util.Collection<Label> replacement) {
        labels.clear();
        labels.addAll(replacement);
    }

    @Setter
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

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
