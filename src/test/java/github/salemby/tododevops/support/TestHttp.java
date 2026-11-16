package github.salemby.tododevops.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

/** One connection pool and bounded requests for local HTTP integration tests. */
public final class TestHttp {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    private TestHttp() {}

    public static HttpClient client() {
        return CLIENT;
    }

    public static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15));
    }
}
