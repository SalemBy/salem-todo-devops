package github.salemby.tododevops.identity.infrastructure.security;

import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

/** Same wire contract as the MVC ProblemDetail advice, including errors. */
public final class SecurityProblems {
    private final ObjectMapper json;
    public SecurityProblems(ObjectMapper json) { this.json = json; }
    public void write(HttpServletRequest request, HttpServletResponse response, int status, String detail) throws IOException {
        write(request, response, status, detail, true);
    }

    /** Without noStore the response keeps Spring Security's default cache headers, as MVC responses do. */
    public void write(HttpServletRequest request, HttpServletResponse response, int status, String detail, boolean noStore)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        if (noStore) response.setHeader("Cache-Control", "no-store");
        json.writeValue(response.getOutputStream(), Map.of("type", "urn:projetotodo:problem:" + status,
            "title", HttpStatus.valueOf(status).getReasonPhrase(), "status", status, "detail", detail,
            "instance", request.getRequestURI(), "errors", List.of()));
    }
}
