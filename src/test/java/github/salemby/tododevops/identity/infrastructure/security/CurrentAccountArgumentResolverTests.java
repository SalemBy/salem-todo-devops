package github.salemby.tododevops.identity.infrastructure.security;

import github.salemby.tododevops.identity.application.AccountPrincipal;
import github.salemby.tododevops.shared.security.CurrentAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurrentAccountArgumentResolverTests {
    private final CurrentAccountArgumentResolver resolver = new CurrentAccountArgumentResolver();

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @SuppressWarnings("unused")
    private void handler(CurrentAccount account, Long other) {}

    private MethodParameter parameter(int index) throws Exception {
        return new MethodParameter(getClass().getDeclaredMethod("handler", CurrentAccount.class, Long.class), index);
    }

    @Test void supportsOnlyCurrentAccountParameters() throws Exception {
        assertTrue(resolver.supportsParameter(parameter(0)));
        assertFalse(resolver.supportsParameter(parameter(1)));
    }

    @Test void resolvesTheSessionAccount() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(42L, 3, Instant.parse("2026-11-18T00:00:00Z")), null, List.of()));
        assertEquals(new CurrentAccount(42L), resolver.resolveArgument(parameter(0), null, null, null));
    }

    @Test void rejectsMissingOrForeignAuthenticationWith401() throws Exception {
        assertUnauthorized();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertUnauthorized();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(
            new AccountPrincipal(42L, 3, Instant.now()), null));
        assertUnauthorized();
    }

    private void assertUnauthorized() throws Exception {
        var parameter = parameter(0);
        var error = assertThrows(ResponseStatusException.class, () -> resolver.resolveArgument(parameter, null, null, null));
        assertEquals(401, error.getStatusCode().value());
    }
}
