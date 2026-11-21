package github.salemby.tododevops.identity.domain;

import github.salemby.tododevops.shared.persistence.Timestamps;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 254, updatable = false)
    private String email;
    @Column(nullable = false, length = 100)
    private String displayName;
    @Column(nullable = false, length = 255)
    private String passwordHash;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private long credentialVersion;
    @Version
    private long version;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public Account(String normalizedEmail, String displayName, String passwordHash) {
        this.email = Objects.requireNonNull(normalizedEmail);
        this.displayName = Objects.requireNonNull(displayName);
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    @PrePersist
    private void onCreate() { createdAt = updatedAt = Timestamps.now(); }
    public void changePassword(String encodedPassword) {
        passwordHash = Objects.requireNonNull(encodedPassword);
        credentialVersion = Math.incrementExact(credentialVersion);
    }
    @PreUpdate
    private void onUpdate() { updatedAt = Timestamps.now(); }
}
