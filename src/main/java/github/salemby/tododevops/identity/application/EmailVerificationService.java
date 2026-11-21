package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RedisRateLimiter;
import github.salemby.tododevops.shared.persistence.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

/** Proves that an account's owner controls its email address (#71). */
@Service
public class EmailVerificationService {
    static final String INVALID = "Verification link is invalid or has expired";
    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private final AccountRepository accounts;
    private final VerificationTokens tokens;
    private final VerificationMailer mailer;
    private final TransactionOperations transactions;
    private final Throttle throttle;
    private final RateLimitProperties limits;
    private final java.time.Duration retention;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;

    public EmailVerificationService(AccountRepository accounts, VerificationTokens tokens, VerificationMailer mailer,
            TransactionOperations transactions, Throttle throttle, RateLimitProperties limits,
            @org.springframework.beans.factory.annotation.Value("${app.auth.unverified-account-retention:7d}")
            java.time.Duration retention, org.springframework.security.crypto.password.PasswordEncoder passwords) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.mailer = mailer;
        this.transactions = transactions;
        this.throttle = throttle;
        this.limits = limits;
        this.retention = retention;
        this.passwords = passwords;
    }

    /**
     * Removes registrations nobody verified within the retention period, which frees their emails. Unverified
     * accounts cannot log in, so they own and belong to nothing that would block the delete.
     */
    @Transactional
    public int deleteStaleUnverified(java.time.Instant now) {
        return accounts.deleteUnverifiedUpdatedBefore(now.minus(retention));
    }

    /**
     * Sends a new token to a pending account, which makes the previous one stop working. The caller learns nothing:
     * unknown, pending and verified emails look the same, and the per-email limit counts all of them.
     */
    public void sendAgain(String email) {
        throttle.hit("verification:" + RedisRateLimiter.emailKey(email), limits.verificationEmailPerEmail());
        var issued = tokens.issue();
        Long pending = transactions.execute(status -> accounts.findLockedByEmail(email).filter(a -> !a.isVerified())
            .map(account -> {
                account.issueVerificationToken(issued.hash(), issued.expiresAt());
                return account.getId();
            }).orElse(null));
        if (pending == null) return;
        try {
            mailer.send(email, issued.token());
        } catch (MailException ex) {
            log.warn("Could not send the verification email to account {}: {}", pending, ex.getMessage());
        }
    }

    /**
     * Verifies the account when the token is valid and the password is the one chosen at registration, so the
     * person who controls the inbox must also be the person who registered. Unknown, expired and used tokens and
     * wrong passwords are refused alike. Not transactional: the hash runs without a connection (#44).
     */
    public void verify(String token, String password) {
        String hash = VerificationTokens.hash(token);
        var account = accounts.findByVerificationTokenHash(hash).orElseThrow(EmailVerificationService::invalid);
        if (!passwords.matches(password, account.getPasswordHash())) throw invalid();
        Integer changed = transactions.execute(status -> accounts.verify(hash, account.getPasswordHash(), Timestamps.now()));
        if (changed == null || changed == 0) throw invalid(); // used, expired or replaced meanwhile
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID);
    }
}
