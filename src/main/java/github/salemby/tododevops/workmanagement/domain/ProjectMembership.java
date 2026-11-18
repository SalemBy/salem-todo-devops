package github.salemby.tododevops.workmanagement.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/** An account's role in a project. The project and account never change; only the role can. */
@Entity
@Table(name = "project_memberships", uniqueConstraints = @UniqueConstraint(
    name = "uk_project_memberships_project_account", columnNames = {"project_id", "account_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    /** Identity owns accounts; this module stores only the ID. */
    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectRole role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public ProjectMembership(Project project, Long accountId, ProjectRole role) {
        this.project = Objects.requireNonNull(project);
        this.accountId = Objects.requireNonNull(accountId);
        this.role = Objects.requireNonNull(role);
    }

    public void changeRole(ProjectRole role) {
        this.role = Objects.requireNonNull(role);
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
