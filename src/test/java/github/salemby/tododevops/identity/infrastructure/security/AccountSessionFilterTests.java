package github.salemby.tododevops.identity.infrastructure.security;

import github.salemby.tododevops.identity.application.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AccountSessionFilterTests {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void absoluteExpiryRejectsExactlyAtBoundaryAndInvalidatesSession() throws Exception {
        var accounts = new AuthenticationService(null, new org.springframework.security.crypto.password.PasswordEncoder() {
            public String encode(CharSequence raw) { return "unused-test-hash"; }
            public boolean matches(CharSequence raw, String encoded) { throw new UnsupportedOperationException(); }
        }, null, null, null) {
            @Override public boolean isValid(AccountPrincipal principal) { return true; }
        };
        Instant login = Instant.parse("2026-11-17T00:00:00Z");
        var principal = new AccountPrincipal(1L, 0, login);
        for (int seconds : new int[]{43199, 43200}) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
            var filter = new AccountSessionFilter(accounts, req -> false, new SecurityProblems(new ObjectMapper()),
                Duration.ofHours(12), Clock.fixed(login.plusSeconds(seconds), ZoneOffset.UTC));
            var request = new MockHttpServletRequest("GET", "/projects");
            var session = new MockHttpSession(); request.setSession(session);
            var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            if (seconds == 43199) { assertNotNull(chain.getRequest()); assertFalse(session.isInvalid()); }
            else { assertNull(chain.getRequest()); assertEquals(401, response.getStatus()); assertTrue(session.isInvalid()); }
            SecurityContextHolder.clearContext();
        }
    }
}
