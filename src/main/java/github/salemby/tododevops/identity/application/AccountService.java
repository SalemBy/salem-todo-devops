package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.identity.application.dto.AccountRegistration;
import github.salemby.tododevops.identity.application.dto.AccountResponse;
import github.salemby.tododevops.identity.domain.Account;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Account creation; HTTP onboarding availability is enforced by the authentication API. */
@Service
@Validated
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    public AccountService(AccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @Transactional
    public AccountResponse create(@NotNull @Valid AccountRegistration request) {
        try {
            var account = accounts.saveAndFlush(new Account(request.email(), request.displayName(),
                passwords.encode(request.password())));
            return new AccountResponse(account.getId(), account.getEmail(), account.getDisplayName(),
                account.isActive(), account.getCreatedAt(), account.getUpdatedAt());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Flush inside the transaction so concurrent registrations receive the same conflict.
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "uk_accounts_email".equals(constraint.getConstraintName()))
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Email is already registered");
            }
            throw ex;
        }
    }
}
