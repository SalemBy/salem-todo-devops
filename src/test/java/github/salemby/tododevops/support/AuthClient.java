package github.salemby.tododevops.support;

import tools.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** One cookie-keeping HTTP client per caller, for the authentication rate-limit tests. */
public final class AuthClient {
    public static final String PASSWORD = "A test only passphrase!";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String base;
    private final HttpClient http;
    private String token, header;

    public AuthClient(int port) {
        base = "http://127.0.0.1:" + port;
        http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5)).build();
    }

    public HttpResponse<String> call(String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json");
        if (token != null) request.header(header, token);
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
        return http.send(request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Fetches a CSRF token; the first call from a new client also creates its session. */
    public HttpResponse<String> csrf() throws Exception {
        var response = call("GET", "/auth/csrf", null);
        if (response.statusCode() == 200) {
            var tree = JSON.readTree(response.body());
            token = tree.get("token").asText();
            header = tree.get("headerName").asText();
        }
        return response;
    }

    public HttpResponse<String> register(String email) throws Exception {
        return call("POST", "/auth/register", Map.of("email", email, "displayName", "Person", "password", PASSWORD));
    }

    public HttpResponse<String> login(String email, String password) throws Exception {
        return call("POST", "/auth/login", Map.of("email", email, "password", password));
    }
}
