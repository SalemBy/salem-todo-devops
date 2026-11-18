package github.salemby.tododevops;

import github.salemby.tododevops.identity.application.dto.AccountRegistration;
import github.salemby.tododevops.identity.infrastructure.security.PasswordConfiguration;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountValidationTests {
    @Test void validatesNormalizedEmailDisplayNameAndPasswordBoundaries() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new AccountRegistration("  Person@EXAMPLE.COM  ", "Person", "a".repeat(15));
            assertEquals("person@example.com", request.email());
            assertTrue(validator.validate(request).isEmpty());
            for (String password : new String[]{"a".repeat(128), "\uD83D\uDD12".repeat(128)})
                assertTrue(validator.validate(new AccountRegistration("a@example.com", "A", password)).isEmpty());
            for (String password : new String[]{null, "", "a".repeat(14), "a".repeat(129), "a".repeat(15) + '\0'})
                assertFalse(validator.validate(new AccountRegistration("a@example.com", "A", password)).isEmpty());
            for (String email : new String[]{null, "", "bad", "a b@example.com", "a".repeat(245) + "@example.com"})
                assertFalse(validator.validate(new AccountRegistration(email, "A", "a".repeat(15))).isEmpty());
            for (String name : new String[]{null, "", "\u200B", "A".repeat(101), "A\0"})
                assertFalse(validator.validate(new AccountRegistration("a@example.com", name, "a".repeat(15))).isEmpty());
            assertFalse(request.toString().contains(request.password()));
        }
    }

    @Test void hashesWithRandomSaltAndDoesNotTruncateOrNormalizePasswords() {
        var encoder = new PasswordConfiguration().passwordEncoder();
        String password = "\uD83D\uDD12".repeat(127) + "A";
        String first = encoder.encode(password), second = encoder.encode(password);
        assertNotEquals(first, second);
        assertTrue(first.startsWith("{pbkdf2-sha256-600k-v1}"));
        assertTrue(encoder.matches(password, first));
        assertFalse(encoder.matches("\uD83D\uDD12".repeat(127) + "B", first));
        String spaced = "  example passphrase  ";
        String hash = encoder.encode(spaced);
        assertTrue(encoder.matches(spaced, hash));
        assertFalse(encoder.matches(spaced.strip(), hash));
    }
}
