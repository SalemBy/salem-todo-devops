package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.identity.application.dto.*;
import github.salemby.tododevops.identity.domain.Account;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RedisRateLimiter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@Validated
public class AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final TransactionOperations transactions;
    private final RedisRateLimiter limiter;
    private final RateLimitProperties limits;
    private final String dummyHash;

    public AuthenticationService(AccountRepository accounts, PasswordEncoder passwords, TransactionOperations transactions,
            RedisRateLimiter limiter, RateLimitProperties limits) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.transactions = transactions;
        this.limiter = limiter;
        this.limits = limits;
        dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    // Not transactional: the hash must not run while a pooled connection is held (#44).
    public AccountPrincipal authenticate(@Valid LoginRequest request) {
        String emailKey = RedisRateLimiter.emailKey(request.email());
        throttle(emailKey); // before the lookup and the hash: a throttled attempt costs one Redis call
        var account = accounts.findByEmail(request.email()).orElse(null);
        boolean matches = passwords.matches(request.password(), account == null ? dummyHash : account.getPasswordHash());
        if (account == null || !matches || !account.isActive()) throw new BadCredentialsException("Invalid credentials");
        try {
            limiter.reset(emailKey);
        } catch (DataAccessException ex) {
            log.warn("Could not reset the login rate-limit count; it expires with its window: {}", ex.getMessage());
            log.debug("Rate-limit reset failure", ex);
        }
        return new AccountPrincipal(account.getId(), account.getCredentialVersion(), Instant.now());
    }

    private void throttle(String emailKey) {
        Optional<Duration> retryAfter;
        try {
            retryAfter = limiter.hit(emailKey, limits.loginPerEmail());
        } catch (DataAccessException ex) {
            log.warn("Rate-limit store unavailable; failing closed: {}", ex.getMessage());
            log.debug("Rate-limit store failure", ex);
            throw problem(HttpStatus.SERVICE_UNAVAILABLE, "Authentication is temporarily unavailable.", ex);
        }
        if (retryAfter.isPresent()) {
            var tooMany = problem(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again later.", null);
            tooMany.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.get().toSeconds()));
            throw tooMany;
        }
    }

    private static ErrorResponseException problem(HttpStatus status, String detail, Throwable cause) {
        var problem = new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), cause);
        problem.getHeaders().setCacheControl("no-store");
        return problem;
    }

    @Transactional(readOnly = true)
    public boolean isValid(AccountPrincipal principal) {
        return accounts.findById(principal.id()).filter(a -> isCurrent(a, principal)).isPresent();
    }

    @Transactional(readOnly = true)
    public AccountResponse current(AccountPrincipal principal) {
        return accounts.findById(principal.id()).filter(a -> isCurrent(a, principal)).map(AccountResponse::from)
            .orElseThrow(AuthenticationService::unauthorized);
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
}
