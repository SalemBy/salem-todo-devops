package github.salemby.tododevops.identity.infrastructure.persistence;

import github.salemby.tododevops.identity.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmail(String normalizedEmail);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Account a where a.email = :email")
    Optional<Account> findLockedByEmail(@org.springframework.data.repository.query.Param("email") String normalizedEmail);

    /**
     * Marks the account with this unexpired token verified and clears the token, in one UPDATE:
     * of two concurrent uses only one matches. Returns how many accounts changed (0 or 1).
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
        update Account a set a.emailVerifiedAt = :now, a.verificationTokenHash = null,
            a.verificationTokenExpiresAt = null, a.updatedAt = :now, a.version = a.version + 1
        where a.verificationTokenHash = :hash and a.verificationTokenExpiresAt > :now""")
    int verify(@org.springframework.data.repository.query.Param("hash") String hash,
               @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Account a where a.id = :id")
    Optional<Account> findLockedById(Long id);

    /** FOR SHARE: concurrent readers proceed, a deactivation (FOR UPDATE) waits for them and they for it. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select a from Account a where a.id = :id")
    Optional<Account> findShareLockedById(Long id);

    /** Locks the given accounts in ID order, so concurrent callers queue instead of deadlocking. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Account a where a.email in :emails order by a.id")
    java.util.List<Account> findLockedByEmailIn(@org.springframework.data.repository.query.Param("emails") java.util.Collection<String> emails);
}
