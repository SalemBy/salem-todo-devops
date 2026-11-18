package github.salemby.tododevops.identity.infrastructure.security;

import github.salemby.tododevops.identity.application.AccountPrincipal;
import github.salemby.tododevops.shared.security.CurrentAccount;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/** Supplies controllers with the session account; AccountSessionFilter has already validated it. */
final class CurrentAccountArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentAccount.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentAccount resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AccountPrincipal principal) {
            return new CurrentAccount(principal.id());
        }
        // Defensive: protected routes never reach a controller without a validated session.
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
