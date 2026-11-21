package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import github.salemby.tododevops.shared.persistence.Timestamps;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Proves that an account's owner controls its email address (#71). */
@Service
public class EmailVerificationService {
    static final String INVALID = "Verification link is invalid or has expired";
    private final AccountRepository accounts;

    public EmailVerificationService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Unknown, expired and used tokens are refused alike. */
    @Transactional
    public void verify(String token) {
        if (accounts.verify(VerificationTokens.hash(token), Timestamps.now()) == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID);
    }
}
