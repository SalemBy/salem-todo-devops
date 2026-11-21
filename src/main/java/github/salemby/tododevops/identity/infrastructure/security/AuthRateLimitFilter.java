package github.salemby.tododevops.identity.infrastructure.security;

import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties.Limit;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/** Per-address limits on the anonymous routes that cost a hash or create a session. Runs before CSRF. */
final class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
    private static final RequestMatcher LOGIN = PATHS.matcher(HttpMethod.POST, "/auth/login");
    private static final RequestMatcher REGISTER = PATHS.matcher(HttpMethod.POST, "/auth/register");
    private static final RequestMatcher CSRF = PATHS.matcher(HttpMethod.GET, "/auth/csrf");

    private final RedisRateLimiter limiter;
    private final RateLimitProperties limits;
    private final SecurityProblems problems;

    AuthRateLimitFilter(RedisRateLimiter limiter, RateLimitProperties limits, SecurityProblems problems) {
        this.limiter = limiter; this.limits = limits; this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String route = null;
        Limit limit = null;
        if (LOGIN.matches(request)) { route = "login"; limit = limits.loginPerAddress(); }
        else if (REGISTER.matches(request)) { route = "register"; limit = limits.registerPerAddress(); }
        // Only a caller without a session gets a new one here; token refreshes in a live session are free.
        else if (CSRF.matches(request) && request.getSession(false) == null) { route = "session"; limit = limits.sessionsPerAddress(); }
        if (route != null) {
            try {
                var retryAfter = limiter.hit(route + ":addr:" + clientKey(request.getRemoteAddr()), limit);
                if (retryAfter.isPresent()) {
                    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.get().toSeconds()));
                    problems.write(request, response, 429, "Too many requests. Try again later.");
                    return;
                }
            } catch (DataAccessException ex) {
                logger.warn("Rate-limit store unavailable; failing closed: " + ex.getMessage());
                logger.debug("Rate-limit store failure", ex);
                problems.write(request, response, 503, "Authentication is temporarily unavailable.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** IPv4 per address; IPv6 per /64, because one client usually controls a whole /64. */
    static String clientKey(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.indexOf(':') < 0) return remoteAddr;
        try {
            var address = InetAddress.getByName(remoteAddr); // an IPv6 literal: parsed, never resolved
            if (address instanceof Inet4Address) return address.getHostAddress(); // IPv4-mapped IPv6
            return HexFormat.of().formatHex(address.getAddress(), 0, 8) + "/64";
        } catch (UnknownHostException ex) {
            return remoteAddr;
        }
    }
}
