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
    /** Null until the owner proves they control the address (#71). */
    private Instant emailVerifiedAt;
    /** SHA-256 of the pending verification token; the raw token exists only in the email. */
    @Column(length = 64)
    private String verificationTokenHash;
    private Instant verificationTokenExpiresAt;
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
    /** Ends every session (the credential version moves) and refuses new logins until reactivated. */
    public void deactivate() {
        active = false;
        credentialVersion = Math.incrementExact(credentialVersion);
    }
    public void reactivate() { active = true; }
    public boolean isVerified() { return emailVerifiedAt != null; }
    /** Replaces any earlier token, which stops working. */
    public void issueVerificationToken(String hash, Instant expiresAt) {
        verificationTokenHash = Objects.requireNonNull(hash);
        verificationTokenExpiresAt = Objects.requireNonNull(expiresAt);
    }
    /** Registering an unverified email again replaces that pending registration (#71). */
    public void replacePendingRegistration(String displayName, String passwordHash) {
        if (isVerified()) throw new IllegalStateException("Only an unverified registration can be replaced");
        this.displayName = Objects.requireNonNull(displayName);
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }
    @PreUpdate
    private void onUpdate() { updatedAt = Timestamps.now(); }
}
