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

    public EmailVerificationService(AccountRepository accounts, VerificationTokens tokens, VerificationMailer mailer,
            TransactionOperations transactions, Throttle throttle, RateLimitProperties limits) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.mailer = mailer;
        this.transactions = transactions;
        this.throttle = throttle;
        this.limits = limits;
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

    /** Unknown, expired and used tokens are refused alike. */
    @Transactional
    public void verify(String token) {
        if (accounts.verify(VerificationTokens.hash(token), Timestamps.now()) == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID);
    }
}
