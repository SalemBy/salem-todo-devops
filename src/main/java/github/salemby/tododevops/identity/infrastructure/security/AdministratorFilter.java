package github.salemby.tododevops.identity.infrastructure.security;

import github.salemby.tododevops.identity.application.AccountPrincipal;
import github.salemby.tododevops.identity.application.Administrators;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * Hides /admin from signed-in non-administrators: they get the 404 an unknown route returns, for any method or body.
 * Runs after CSRF, like an unknown route, and before MVC, which would answer a wrong method with a revealing 405.
 * The exception Spring throws for an unmapped path goes through MVC's own resolver, so the response is the same
 * byte for byte (#67).
 */
final class AdministratorFilter extends OncePerRequestFilter {
    private final Administrators administrators;
    private final RequestMatcher adminRoutes;
    private final HandlerExceptionResolver resolver;

    AdministratorFilter(Administrators administrators, RequestMatcher adminRoutes, HandlerExceptionResolver resolver) {
        this.administrators = administrators; this.adminRoutes = adminRoutes; this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (adminRoutes.matches(request) && !(auth != null && auth.getPrincipal() instanceof AccountPrincipal principal
                && administrators.isAdministrator(principal.id()))) {
            String uri = request.getRequestURI();
            resolver.resolveException(request, response, null,
                new NoResourceFoundException(HttpMethod.valueOf(request.getMethod()), uri, uri.substring(1)));
            return;
        }
        chain.doFilter(request, response);
    }
}
