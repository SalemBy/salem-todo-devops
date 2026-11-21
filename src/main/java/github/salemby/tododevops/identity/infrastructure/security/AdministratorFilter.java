package github.salemby.tododevops.identity.infrastructure.security;

import github.salemby.tododevops.identity.application.AccountPrincipal;
import github.salemby.tododevops.identity.application.Administrators;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Hides /admin from signed-in non-administrators: they get the 404 an unknown route returns, for any method or body.
 * Runs after CSRF, like an unknown route, and before MVC, which would answer a wrong method with a revealing 405 (#67).
 */
final class AdministratorFilter extends OncePerRequestFilter {
    private final Administrators administrators;
    private final RequestMatcher adminRoutes;
    private final SecurityProblems problems;

    AdministratorFilter(Administrators administrators, RequestMatcher adminRoutes, SecurityProblems problems) {
        this.administrators = administrators; this.adminRoutes = adminRoutes; this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (adminRoutes.matches(request) && !(auth != null && auth.getPrincipal() instanceof AccountPrincipal principal
                && administrators.isAdministrator(principal.id()))) {
            // Same wording as Spring MVC's NoResourceFoundException for an unmapped path.
            problems.write(request, response, 404, "No static resource " + request.getRequestURI().substring(1) + ".", false);
            return;
        }
        chain.doFilter(request, response);
    }
}
