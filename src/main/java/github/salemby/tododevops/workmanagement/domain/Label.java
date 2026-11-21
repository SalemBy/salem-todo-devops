package github.salemby.tododevops.workmanagement.domain;

import github.salemby.tododevops.shared.persistence.Timestamps;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "labels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Label {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Label(Project project, String name) {
        this.project = java.util.Objects.requireNonNull(project);
        this.name = name;
    }

    public void rename(String name) { this.name = name; }
    public void archive() { active = false; }

    @PrePersist
    private void onCreate() { createdAt = updatedAt = Timestamps.now(); }

    @PreUpdate
    private void onUpdate() { updatedAt = Timestamps.now(); }
}
