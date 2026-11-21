package github.salemby.tododevops;

import github.salemby.tododevops.identity.application.AuthenticationService;
import github.salemby.tododevops.identity.application.dto.LoginRequest;
import github.salemby.tododevops.identity.domain.Account;
import github.salemby.tododevops.identity.infrastructure.persistence.AccountRepository;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties.Limit;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.ErrorResponseException;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The email limit has no notion of client address, so it holds whichever address the attempts come from. */
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationServiceTests {
    private static final String EMAIL = "ana@example.com";
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final RedisRateLimiter limiter = mock(RedisRateLimiter.class);
    private final Limit perEmail = new Limit(5, Duration.ofMinutes(15));
    private final AuthenticationService service = service();

    @Test
    void throttledLoginNeverHashesOrReadsTheAccount() {
        when(limiter.hit(RedisRateLimiter.emailKey(EMAIL), perEmail)).thenReturn(Optional.of(Duration.ofSeconds(7)));
        var result = assertThrows(ErrorResponseException.class, () -> service.authenticate(new LoginRequest(EMAIL, "guess")));
        assertEquals(429, result.getStatusCode().value());
        assertEquals("7", result.getHeaders().getFirst("Retry-After"));
        assertEquals("Too many requests. Try again later.", result.getBody().getDetail());
        verify(passwords, never()).matches(any(), any());
        verifyNoInteractions(accounts);
    }

    @Test
    void redisFailureFailsClosedWithoutHashing() {
        when(limiter.hit(any(), any())).thenThrow(new RedisConnectionFailureException("down"));
        var result = assertThrows(ErrorResponseException.class, () -> service.authenticate(new LoginRequest(EMAIL, "guess")));
        assertEquals(503, result.getStatusCode().value());
        assertNull(result.getHeaders().getFirst("Retry-After"));
        assertEquals("Authentication is temporarily unavailable.", result.getBody().getDetail());
        verify(passwords, never()).matches(any(), any());
        verifyNoInteractions(accounts);
    }

    @Test
    void redisFailureIsLoggedOnceWithoutAStackTrace(CapturedOutput output) {
        when(limiter.hit(any(), any())).thenThrow(new RedisConnectionFailureException("redis is down"));
        assertThrows(ErrorResponseException.class, () -> service.authenticate(new LoginRequest(EMAIL, "guess")));
        assertTrue(output.getOut().contains("WARN"), output.getOut());
        assertTrue(output.getOut().contains("redis is down"), output.getOut());
        assertFalse(output.getOut().contains("\tat "), "stack trace logged at WARN:\n" + output.getOut());
    }

    @Test
    void successfulLoginResetsTheEmailCount() {
        when(limiter.hit(any(), any())).thenReturn(Optional.empty());
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(new Account(EMAIL, "Ana", "stored")));
        when(passwords.matches("right", "stored")).thenReturn(true);
        service.authenticate(new LoginRequest(EMAIL, "right"));
        verify(limiter).reset(RedisRateLimiter.emailKey(EMAIL));
    }

    @Test
    void failedLoginKeepsTheEmailCount() {
        when(limiter.hit(any(), any())).thenReturn(Optional.empty());
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(new Account(EMAIL, "Ana", "stored")));
        assertThrows(BadCredentialsException.class, () -> service.authenticate(new LoginRequest(EMAIL, "wrong")));
        verify(limiter, never()).reset(any());
    }

    @Test
    void resetFailureDoesNotFailASuccessfulLogin() {
        when(limiter.hit(any(), any())).thenReturn(Optional.empty());
        when(accounts.findByEmail(EMAIL)).thenReturn(Optional.of(new Account(EMAIL, "Ana", "stored")));
        when(passwords.matches("right", "stored")).thenReturn(true);
        doThrow(new RedisConnectionFailureException("down")).when(limiter).reset(eq(RedisRateLimiter.emailKey(EMAIL)));
        assertNotNull(service.authenticate(new LoginRequest(EMAIL, "right")));
    }

    private AuthenticationService service() {
        when(passwords.encode(any())).thenReturn("dummy");
        var limits = new RateLimitProperties("rl:", perEmail, perEmail, perEmail, perEmail);
        return new AuthenticationService(accounts, passwords, null, limiter, limits);
    }
}
