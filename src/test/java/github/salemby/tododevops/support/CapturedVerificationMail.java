package github.salemby.tododevops.support;

import github.salemby.tododevops.identity.application.VerificationTokens;
import github.salemby.tododevops.identity.application.VerificationMailer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only mailer for the postgres-test profile (#71): records every verification email and verifies the account
 * at once, so tests that are not about verification keep registering and logging in. Addresses on
 * {@link #UNVERIFIED_DOMAIN} stay unverified, for the tests that exercise the real flow. Production has no such switch.
 */
@Component
@Primary
@Profile("postgres-test")
public class CapturedVerificationMail implements VerificationMailer {
    public static final String UNVERIFIED_DOMAIN = "@unverified.example.com";
    private static final Map<String, List<String>> SENT = new ConcurrentHashMap<>();
    private final ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbc;

    public CapturedVerificationMail(ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    /** The tokens sent to the address, oldest first; shared by every test context. */
    public static List<String> sentTo(String email) {
        return List.copyOf(SENT.getOrDefault(email, List.of()));
    }

    @Override
    public void send(String email, String token) {
        SENT.computeIfAbsent(email, e -> new CopyOnWriteArrayList<>()).add(token);
        // Verifying needs the registration password, which a mailer never sees, so mark the account directly.
        if (!email.endsWith(UNVERIFIED_DOMAIN)) jdbc.getObject().update("UPDATE accounts SET email_verified_at = now(),"
            + " verification_token_hash = NULL, verification_token_expires_at = NULL WHERE verification_token_hash = ?",
            VerificationTokens.hash(token));
    }
}
