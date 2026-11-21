package github.salemby.tododevops;

import github.salemby.tododevops.identity.application.AccountService;
import github.salemby.tododevops.identity.application.VerificationMailer;
import github.salemby.tododevops.identity.application.VerificationTokens;
import github.salemby.tododevops.identity.application.dto.AccountRegistration;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static github.salemby.tododevops.support.PostgresFailures.integrityViolation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTests {
    private final AccountRepository repository = mock(AccountRepository.class);
    private final VerificationMailer mailer = mock(VerificationMailer.class);
    private final AccountService service = new AccountService(repository, encoder(), TransactionOperations.withoutTransaction(),
        new VerificationTokens(Duration.ofHours(24)), mailer);
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

    @Test
    void mailFailureStillRegisters() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        doThrow(new MailSendException("down")).when(mailer).send(any(), any());
        var created = service.create(registration);
        assertFalse(created.emailVerified());
        verify(mailer).send(eq("ana@example.com"), any());
    }

    private static PasswordEncoder encoder() {
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("hash");
        return encoder;
    }
}
