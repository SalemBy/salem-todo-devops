package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.identity.domain.Account;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import github.salemby.tododevops.shared.validation.Emails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * System administrators: active accounts whose email is listed in app.admin.emails (#67).
 * Nothing is seeded; an email with no registered account grants nothing. Account emails never change.
 */
@Service
public class Administrators {
    private final AccountRepository accounts;
    private final Set<String> emails;

    public Administrators(AccountRepository accounts, @Value("${app.admin.emails:}") List<String> configured) {
        this.accounts = accounts;
        this.emails = configured.stream().map(Emails::normalize).filter(Objects::nonNull).filter(e -> !e.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public boolean isAdministrator(Long accountId) {
        return accounts.findById(accountId).filter(this::isAdministrator).isPresent();
    }

    /** The configured administrator emails, normalized. */
    Set<String> emails() {
        return emails;
    }

    boolean isAdministrator(Account account) {
        return account.isActive() && account.isVerified() && emails.contains(account.getEmail());
    }

    boolean isConfigured(Account account) {
        return emails.contains(account.getEmail());
    }
}
