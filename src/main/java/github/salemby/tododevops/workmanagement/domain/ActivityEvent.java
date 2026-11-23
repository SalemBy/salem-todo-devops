package github.salemby.tododevops.workmanagement.domain;

import github.salemby.tododevops.shared.persistence.Timestamps;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One append-only entry of a project's activity history (#12). Written once, never changed. */
@Entity
@Immutable
@Table(name = "activity_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    /** Set for task and comment events. */
    @Column(name = "task_id", updatable = false)
    private Long taskId;

    @Column(name = "actor_account_id", nullable = false, updatable = false)
    private Long actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false, length = 20)
    private ResourceType resourceType;

    /** The project, task, label or comment ID, or the member's account ID. */
    @Column(name = "resource_id", nullable = false, updatable = false)
    private Long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ActivityAction action;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @ElementCollection
    @CollectionTable(name = "activity_changes", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "position")
    private List<FieldChange> changes = new ArrayList<>();

    public ActivityEvent(long projectId, Long taskId, long actorAccountId, ResourceType resourceType, long resourceId,
                         ActivityAction action, List<FieldChange> changes) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.actorAccountId = actorAccountId;
        this.resourceType = Objects.requireNonNull(resourceType);
        this.resourceId = resourceId;
        this.action = Objects.requireNonNull(action);
        this.changes = new ArrayList<>(changes);
        this.occurredAt = Timestamps.now();
    }
}
