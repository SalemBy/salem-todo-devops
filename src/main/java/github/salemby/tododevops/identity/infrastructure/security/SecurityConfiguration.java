package github.salemby.tododevops.identity.infrastructure.security;

import github.salemby.tododevops.identity.application.AuthenticationService;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RateLimitProperties;
import github.salemby.tododevops.identity.infrastructure.ratelimit.RedisRateLimiter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.*;
import org.springframework.session.web.http.SessionRepositoryFilter;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;

@Configuration
public class SecurityConfiguration {
    @Bean org.springframework.security.authentication.AuthenticationManager authenticationManager(AuthenticationService accounts) {
        return input -> {
            var principal = accounts.authenticate(new github.salemby.tododevops.identity.application.dto.LoginRequest(
                input.getName(), (String) input.getCredentials()));
            return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        };
    }
    @Bean SecurityContextRepository securityContextRepository() {
        var sessions = new HttpSessionSecurityContextRepository();
        sessions.setDisableUrlRewriting(true);
        return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(), sessions);
    }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }
    /** Runs just outside Spring Session's filter, so a failing session store becomes a 503 problem response. */
    @Bean FilterRegistrationBean<SessionStoreFailureFilter> sessionStoreFailureFilter(ObjectMapper json) {
        var registration = new FilterRegistrationBean<>(new SessionStoreFailureFilter(new SecurityProblems(json)));
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 1);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationService accounts, ObjectMapper json,
            github.salemby.tododevops.identity.application.Administrators administrators,
            RedisRateLimiter limiter, RateLimitProperties limits,
            SecurityContextRepository contexts, CsrfTokenRepository csrf,
            @Value("${app.auth.absolute-timeout:12h}") Duration absoluteTimeout) throws Exception {
        if (absoluteTimeout.isZero() || absoluteTimeout.isNegative()) throw new IllegalArgumentException("Absolute session timeout must be positive");
        var paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher publicRoutes = new OrRequestMatcher(paths.matcher(HttpMethod.GET, "/"),
            paths.matcher(HttpMethod.GET, "/auth/csrf"), paths.matcher(HttpMethod.POST, "/auth/register"),
            paths.matcher(HttpMethod.POST, "/auth/login"));
        var problems = new SecurityProblems(json);
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
            .securityContext(c -> c.securityContextRepository(contexts).requireExplicitSave(true))
            .csrf(c -> c.csrfTokenRepository(csrf))
            .exceptionHandling(c -> c.authenticationEntryPoint((req, res, ex) -> problems.write(req, res, 401, "Authentication required"))
                .accessDeniedHandler((req, res, ex) -> problems.write(req, res, 403, "Request forbidden")))
            .authorizeHttpRequests(c -> c.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(publicRoutes).permitAll().anyRequest().authenticated())
            .addFilterBefore(new AuthRateLimitFilter(limiter, limits, problems), CsrfFilter.class)
            .addFilterBefore(new AccountSessionFilter(accounts, publicRoutes, problems, absoluteTimeout, Clock.systemUTC()), CsrfFilter.class)
            .addFilterAfter(new AdministratorFilter(administrators, paths.matcher("/admin/**"), problems), CsrfFilter.class);
        return http.build();
    }
}
