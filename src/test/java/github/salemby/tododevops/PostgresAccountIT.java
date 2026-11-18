package github.salemby.tododevops;

import github.salemby.tododevops.identity.application.AccountService;
import github.salemby.tododevops.identity.application.dto.AccountRegistration;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.ConstraintViolationException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresAccountIT {
    @Autowired AccountService service;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder passwords;

    @Test void persistsOnlyHashAndReservesNormalizedEmail() {
        String email = UUID.randomUUID() + "@example.com";
        String raw = "An example passphrase";
        var response = service.create(new AccountRegistration(" " + email.toUpperCase(java.util.Locale.ROOT) + " ", "Person", raw));
        try {
            var stored = accounts.findById(response.id()).orElseThrow();
            assertEquals(email, stored.getEmail());
            assertTrue(stored.isActive());
            assertEquals(0, stored.getCredentialVersion());
            assertNotNull(stored.getCreatedAt());
            assertEquals(stored.getCreatedAt(), stored.getUpdatedAt());
            assertNotEquals(raw, stored.getPasswordHash());
            assertTrue(passwords.matches(raw, stored.getPasswordHash()));
            String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(response);
            assertFalse(json.contains("password"));
            assertFalse(json.contains(raw));
            assertFalse(json.contains(stored.getPasswordHash()));
            var duplicate = assertThrows(ResponseStatusException.class, () -> service.create(new AccountRegistration(email, "Duplicate", raw)));
            assertEquals(409, duplicate.getStatusCode().value());
        } finally { accounts.deleteById(response.id()); }
    }

    @Test void rejectsInvalidRequestsAtServiceBoundaryWithoutPersisting() {
        long before = accounts.count();
        assertThrows(ConstraintViolationException.class, () -> service.create(new AccountRegistration("invalid", "Person", "short")));
        assertThrows(ConstraintViolationException.class, () -> service.create(null));
        assertEquals(before, accounts.count());
    }
}
