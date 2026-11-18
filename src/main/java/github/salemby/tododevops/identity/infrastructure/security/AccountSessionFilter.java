package github.salemby.tododevops.identity.infrastructure.security;

import github.salemby.tododevops.identity.application.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.*;

/** Runs after context loading and before CSRF: absent/revoked identities receive 401. */
final class AccountSessionFilter extends OncePerRequestFilter {
    private final AuthenticationService accounts;
    private final RequestMatcher publicRoutes;
    private final SecurityProblems problems;
    private final Duration absoluteTimeout;
    private final Clock clock;

    AccountSessionFilter(AuthenticationService accounts, RequestMatcher publicRoutes, SecurityProblems problems,
            Duration absoluteTimeout, Clock clock) {
        this.accounts = accounts; this.publicRoutes = publicRoutes; this.problems = problems;
        this.absoluteTimeout = absoluteTimeout; this.clock = clock;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean valid = auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AccountPrincipal;
        if (valid) {
            var principal = (AccountPrincipal) auth.getPrincipal();
            valid = clock.instant().isBefore(principal.authenticatedAt().plus(absoluteTimeout)) && accounts.isValid(principal);
        }
        if (auth != null && !valid) new SecurityContextLogoutHandler().logout(request, response, auth);
        if (!valid && !publicRoutes.matches(request)) {
            problems.write(request, response, 401, "Authentication required");
            return;
        }
        chain.doFilter(request, response);
    }
}
