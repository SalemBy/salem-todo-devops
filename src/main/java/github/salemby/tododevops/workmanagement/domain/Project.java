package github.salemby.tododevops.workmanagement.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "projects", uniqueConstraints = @UniqueConstraint(name = "uk_projects_key", columnNames = "project_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "project_key", nullable = false, updatable = false, length = 10)
    private String key;

    @Setter
    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    /** The account that created the project. Never changes, even if that account leaves. */
    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Project(String name, String key, String description, Long createdByAccountId) {
        this.name = name;
        this.key = key;
        this.description = description;
        this.createdByAccountId = java.util.Objects.requireNonNull(createdByAccountId);
    }

    public void archive() {
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
