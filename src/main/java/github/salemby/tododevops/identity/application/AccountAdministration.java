package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.identity.application.dto.AccountResponse;
import github.salemby.tododevops.identity.domain.Account;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Turns accounts off and on for system administrators (#67). Callers check that the caller is an administrator. */
@Service
public class AccountAdministration {
    static final String LAST_ADMINISTRATOR = "At least one active administrator is required";
    private final AccountRepository accounts;
    private final Administrators administrators;

    public AccountAdministration(AccountRepository accounts, Administrators administrators) {
        this.accounts = accounts;
        this.administrators = administrators;
    }

    /**
     * Locks the account row, so assignments checked under its shared lock finish first, then disables it and
     * moves its credential version: every session fails its next check and reactivation revives none.
     */
    @Transactional
    public AccountResponse deactivate(Long accountId) {
        // Every configured administrator row first, in ID order: two administrators deactivating each other
        // queue here, and the second one sees the first change before it checks for another active administrator.
        var admins = administrators.emails().isEmpty() ? List.<Account>of() : accounts.findLockedByEmailIn(administrators.emails());
        var account = locked(accountId);
        if (administrators.isConfigured(account)
                && admins.stream().noneMatch(a -> !a.getId().equals(accountId) && a.isActive()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, LAST_ADMINISTRATOR);
        if (account.isActive()) account.deactivate();
        accounts.flush();
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse reactivate(Long accountId) {
        var account = locked(accountId);
        account.reactivate();
        accounts.flush();
        return AccountResponse.from(account);
    }

    private Account locked(Long accountId) {
        return accounts.findLockedById(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }
}
