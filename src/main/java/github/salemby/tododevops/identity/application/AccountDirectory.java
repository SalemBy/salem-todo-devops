package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.shared.validation.Emails;
import github.salemby.tododevops.identity.application.dto.AccountSummary;
import github.salemby.tododevops.identity.domain.Account;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only account lookups for other modules. Never exposes credentials or entities. */
@Service
public class AccountDirectory {
    private final AccountRepository accounts;

    public AccountDirectory(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Finds an active account by email, normalized the same way as registration. */
    @Transactional(readOnly = true)
    public Optional<AccountSummary> findActiveByEmail(String email) {
        if (email == null) return Optional.empty();
        return accounts.findByEmail(Emails.normalize(email))
            .filter(AccountDirectory::usable).map(AccountDirectory::summary);
    }

    /** Summaries for the given IDs in one query, keyed by ID. Unknown IDs are absent. */
    @Transactional(readOnly = true)
    public Map<Long, AccountSummary> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return accounts.findAllById(ids).stream().map(AccountDirectory::summary)
            .collect(Collectors.toMap(AccountSummary::id, Function.identity()));
    }

    /**
     * Whether the account is active, holding a shared lock on its row until the transaction ends,
     * so a concurrent deactivation runs entirely before or after the caller (#67).
     */
    @Transactional
    public boolean isActiveLocked(Long accountId) {
        return accounts.findShareLockedById(accountId).filter(AccountDirectory::usable).isPresent();
    }

    private static AccountSummary summary(Account account) {
        return new AccountSummary(account.getId(), account.getEmail(), account.getDisplayName(), usable(account));
    }

    /** Other modules see an account as active only once it is enabled and its email is verified (#71). */
    private static boolean usable(Account account) {
        return account.isActive() && account.isVerified();
    }
}
