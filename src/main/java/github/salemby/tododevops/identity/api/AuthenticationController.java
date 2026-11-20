package github.salemby.tododevops.identity.api;

import github.salemby.tododevops.identity.application.*;
import github.salemby.tododevops.identity.application.dto.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {
    private final AccountService accounts;
    private final AuthenticationService authentication;
    private final AuthenticationManager manager;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final boolean registrationEnabled;

    public AuthenticationController(AccountService accounts, AuthenticationService authentication,
            SecurityContextRepository contexts, SessionAuthenticationStrategy sessions, AuthenticationManager manager,
            @Value("${app.auth.registration-enabled:false}") boolean registrationEnabled) {
        this.accounts = accounts; this.authentication = authentication; this.contexts = contexts;
        this.manager = manager;
        this.sessions = sessions; this.registrationEnabled = registrationEnabled;
    }

    public record CsrfResponse(String token, String headerName) {}
    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CsrfResponse(token.getToken(), token.getHeaderName()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody AccountRegistration request) {
        if (!registrationEnabled) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration is disabled");
        return accounts.create(request);
    }

    @PostMapping("/login")
    public AccountResponse login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        org.springframework.security.core.Authentication auth;
        try { auth = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password())); }
        catch (BadCredentialsException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"); }
        var principal = (AccountPrincipal) auth.getPrincipal();
        sessions.onAuthentication(auth, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return authentication.current(principal);
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) { return authentication.current(principal); }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) { clearSession(request, response); }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody PasswordChangeRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        authentication.changePassword(principal, input);
        clearSession(request, response);
    }

    // Invalidating the session deletes it from Redis; Spring Session then expires the cookie.
    private void clearSession(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }
}
