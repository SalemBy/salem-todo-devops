package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.identity.application.dto.AccountRegistration;
import github.salemby.tododevops.identity.application.dto.AccountResponse;
import github.salemby.tododevops.identity.domain.Account;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import github.salemby.tododevops.shared.persistence.UniqueConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

/** Account creation; HTTP onboarding availability is enforced by the authentication API. */
@Service
@Validated
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final TransactionOperations transactions;
    private final VerificationTokens tokens;
    private final VerificationMailer mailer;

    public AccountService(AccountRepository accounts, PasswordEncoder passwords, TransactionOperations transactions,
            VerificationTokens tokens, VerificationMailer mailer) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.transactions = transactions;
        this.tokens = tokens;
        this.mailer = mailer;
    }

    /**
     * Creates an unverified account, or replaces a pending (unverified) registration of the same email, then emails
     * the verification token after the commit (#71). The hash runs before any connection is held (#44).
     */
    public AccountResponse create(@NotNull @Valid AccountRegistration request) {
        String hash = passwords.encode(request.password());
        var issued = tokens.issue();
        AccountResponse created;
        try {
            created = transactions.execute(status -> {
                var account = accounts.findLockedByEmail(request.email()).map(pending -> {
                    if (pending.isVerified()) throw alreadyRegistered();
                    pending.replacePendingRegistration(request.displayName(), hash);
                    return pending;
                }).orElseGet(() -> new Account(request.email(), request.displayName(), hash));
                account.issueVerificationToken(issued.hash(), issued.expiresAt());
                return AccountResponse.from(accounts.saveAndFlush(account));
            });
        } catch (DataIntegrityViolationException ex) {
            // A concurrent first registration of the same email loses on the unique constraint.
            if (UniqueConstraints.violated(ex, "uk_accounts_email")) throw alreadyRegistered();
            throw ex;
        }
        try {
            mailer.send(created.email(), issued.token());
        } catch (MailException ex) {
            // The account stays pending; its owner can ask for another email. Never log the token.
            log.warn("Could not send the verification email to account {}: {}", created.id(), ex.getMessage());
        }
        return created;
    }

    private static ResponseStatusException alreadyRegistered() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
    }
}
