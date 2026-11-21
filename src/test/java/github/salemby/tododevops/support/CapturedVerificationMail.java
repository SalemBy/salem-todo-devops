package github.salemby.tododevops.support;

import github.salemby.tododevops.identity.application.EmailVerificationService;
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
    private final ObjectProvider<EmailVerificationService> verification;

    public CapturedVerificationMail(ObjectProvider<EmailVerificationService> verification) {
        this.verification = verification;
    }

    /** The tokens sent to the address, oldest first; shared by every test context. */
    public static List<String> sentTo(String email) {
        return List.copyOf(SENT.getOrDefault(email, List.of()));
    }

    @Override
    public void send(String email, String token) {
        SENT.computeIfAbsent(email, e -> new CopyOnWriteArrayList<>()).add(token);
        if (!email.endsWith(UNVERIFIED_DOMAIN)) verification.getObject().verify(token);
    }
}
