package github.salemby.tododevops.shared.observability;

import io.micrometer.common.KeyValue;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Requests that security filters answer before routing (401, 403, 429, 503) have no route
 * template, so the default {@code uri} tag is {@code UNKNOWN}. The fixed public auth paths carry
 * no IDs, so they are named; every other path keeps the default and never its raw value.
 */
@Component
class RouteTagConvention extends DefaultServerRequestObservationConvention {
    static final Set<String> PUBLIC_PATHS =
        Set.of("/auth/csrf", "/auth/login", "/auth/register", "/auth/logout", "/auth/me", "/auth/password");

    @Override
    protected KeyValue uri(ServerRequestObservationContext context) {
        String path = context.getCarrier().getRequestURI();
        if (context.getPathPattern() == null && PUBLIC_PATHS.contains(path)) return KeyValue.of("uri", path);
        return super.uri(context);
    }
}
