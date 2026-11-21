package github.salemby.tododevops.identity.application;

import github.salemby.tododevops.shared.persistence.Timestamps;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Single-use email verification tokens: 256 random bits, stored only as their SHA-256 (#71). */
@Component
public class VerificationTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Duration ttl;

    /** The raw token goes only into the email; the hash and expiry go to the account. */
    public record Issued(String token, String hash, Instant expiresAt) {}

    public VerificationTokens(@Value("${app.auth.verification-token-ttl:24h}") Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("Verification token lifetime must be positive");
        this.ttl = ttl;
    }

    public Issued issue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(token, hash(token), Timestamps.now().plus(ttl));
    }

    public Duration ttl() {
        return ttl;
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
