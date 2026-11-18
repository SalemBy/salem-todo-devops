package github.salemby.tododevops.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.ObjectMapper;

/** One connection pool and bounded requests for local HTTP integration tests. */
public final class TestHttp {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();
    private static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<>();

    /** A logged-in disposable account on one isolated server. */
    public record Session(String base, String cookie, String token, String header, long accountId, String email) {
        public HttpRequest.Builder request(String path) {
            return HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                .header("Cookie", cookie).header(header, token);
        }
    }

    private TestHttp() {}

    public static HttpClient client() {
        return CLIENT;
    }

    /** A request authenticated as the server's shared default test account. */
    public static HttpRequest.Builder request(URI uri) {
        var session = defaultSession(uri);
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
            .header("Cookie", session.cookie()).header(session.header(), session.token());
    }

    /** The account ID behind {@link #request}, for fixtures that must belong to that account. */
    public static long accountId(URI anyUriOnServer) {
        return defaultSession(anyUriOnServer).accountId();
    }

    /** Registers and logs in a new, separate account, for tests that need several callers. */
    public static Session newSession(URI anyUriOnServer) {
        return login(base(anyUriOnServer));
    }

    private static Session defaultSession(URI uri) {
        return SESSIONS.computeIfAbsent(base(uri), TestHttp::login);
    }

    private static String base(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    /** Creates a disposable account through production HTTP security. */
    private static Session login(String base) {
        try {
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            var client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
            var json = new ObjectMapper();
            var csrf = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
            var token = json.readTree(csrf.body());
            String email = UUID.randomUUID() + "@example.com", password = UUID.randomUUID().toString();
            for (String path : new String[]{"/auth/register", "/auth/login"}) {
                var body = json.writeValueAsString(java.util.Map.of("email", email, "password", password, "displayName", "HTTP test"));
                var result = client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json").header(token.get("headerName").asText(), token.get("token").asText())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                if (result.statusCode() != (path.endsWith("register") ? 201 : 200))
                    throw new IllegalStateException("Test authentication failed: " + result.statusCode() + " " + result.body());
            }
            token = json.readTree(client.send(HttpRequest.newBuilder(URI.create(base + "/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
            var me = json.readTree(client.send(HttpRequest.newBuilder(URI.create(base + "/auth/me")).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
            String cookie = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                .map(c -> c.getName() + "=" + c.getValue()).findFirst().orElseThrow();
            return new Session(base, cookie, token.get("token").asText(), token.get("headerName").asText(), me.get("id").asLong(), email);
        } catch (Exception ex) { throw new IllegalStateException("Cannot authenticate isolated HTTP tests", ex); }
    }
}
