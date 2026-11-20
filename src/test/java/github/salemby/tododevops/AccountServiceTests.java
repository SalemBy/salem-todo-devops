package github.salemby.tododevops;

import github.salemby.tododevops.identity.application.AccountService;
import github.salemby.tododevops.identity.application.dto.AccountRegistration;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static github.salemby.tododevops.support.PostgresFailures.integrityViolation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTests {
    private final AccountRepository repository = mock(AccountRepository.class);
    private final AccountService service = new AccountService(repository, encoder());
    private final AccountRegistration registration = new AccountRegistration("ana@example.com", "Ana", "correct horse battery");

    @Test
    void translatesLocalizedDuplicateEmail() {
        when(repository.saveAndFlush(any())).thenThrow(integrityViolation("23505", "uk_accounts_email"));
        var result = assertThrows(ResponseStatusException.class, () -> service.create(registration));
        assertEquals(409, result.getStatusCode().value());
        assertEquals("Email is already registered", result.getReason());
    }

    @Test
    void rethrowsUnrelatedIntegrityFailures() {
        var failure = integrityViolation("23502", null);
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.create(registration)));
    }

    @Test
    void rethrowsDriverErrorsWithoutServerDetails() {
        var failure = new DataIntegrityViolationException("x", new PSQLException("client error", PSQLState.UNIQUE_VIOLATION));
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.create(registration)));
    }

    private static PasswordEncoder encoder() {
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash");
        return encoder;
    }
}
