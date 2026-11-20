package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.identity.application.dto.*;
import github.salemby.tododevops.identity.domain.Account;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@Service
@Validated
public class AuthenticationService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final TransactionOperations transactions;
    private final String dummyHash;

    public AuthenticationService(AccountRepository accounts, PasswordEncoder passwords, TransactionOperations transactions) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.transactions = transactions;
        dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    // Not transactional: the hash must not run while a pooled connection is held (#44).
    public AccountPrincipal authenticate(@Valid LoginRequest request) {
        var account = accounts.findByEmail(request.email()).orElse(null);
        boolean matches = passwords.matches(request.password(), account == null ? dummyHash : account.getPasswordHash());
        if (account == null || !matches || !account.isActive()) throw new BadCredentialsException("Invalid credentials");
        return new AccountPrincipal(account.getId(), account.getCredentialVersion(), Instant.now());
    }

    @Transactional(readOnly = true)
    public boolean isValid(AccountPrincipal principal) {
        return accounts.findById(principal.id()).filter(a -> a.isActive()
            && a.getCredentialVersion() == principal.credentialVersion()).isPresent();
    }

    @Transactional(readOnly = true)
    public AccountResponse current(AccountPrincipal principal) {
        var a = accounts.findById(principal.id()).filter(account -> account.isActive()
            && account.getCredentialVersion() == principal.credentialVersion())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        return response(a);
    }

    // Hashes run before the locking transaction, which then rechecks the account (#44).
    public void changePassword(AccountPrincipal principal, @Valid PasswordChangeRequest request) {
        var account = accounts.findById(principal.id()).filter(a -> isCurrent(a, principal))
            .orElseThrow(AuthenticationService::unauthorized);
        if (!passwords.matches(request.currentPassword(), account.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        String encoded = passwords.encode(request.newPassword());
        transactions.executeWithoutResult(status -> {
            var locked = accounts.findForPasswordChange(principal.id()).filter(a -> isCurrent(a, principal))
                .orElseThrow(AuthenticationService::unauthorized); // concurrent change or disable
            locked.changePassword(encoded);
            accounts.flush();
        });
    }

    private static boolean isCurrent(Account a, AccountPrincipal principal) {
        return a.isActive() && a.getCredentialVersion() == principal.credentialVersion();
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    private static AccountResponse response(Account a) {
        return new AccountResponse(a.getId(), a.getEmail(), a.getDisplayName(), a.isActive(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
