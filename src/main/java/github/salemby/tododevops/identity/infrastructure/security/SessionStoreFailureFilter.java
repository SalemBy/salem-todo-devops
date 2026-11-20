package github.salemby.tododevops.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Wraps Spring Session's filter: when the session store (Redis) fails while loading or saving a
 * session, the caller gets the API's 503 problem response instead of a container error page.
 */
final class SessionStoreFailureFilter extends OncePerRequestFilter {
    private final SecurityProblems problems;

    SessionStoreFailureFilter(SecurityProblems problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (DataAccessException ex) {
            if (response.isCommitted()) throw ex;
            logger.warn("Session store unavailable; failing closed: " + ex.getMessage());
            logger.debug("Session store failure", ex);
            response.resetBuffer(); // keeps the security headers already written (nosniff, frame options)
            problems.write(request, response, 503, "Authentication is temporarily unavailable.");
        }
    }
}
