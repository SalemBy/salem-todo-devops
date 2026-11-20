package github.salemby.tododevops;

import github.salemby.tododevops.support.AuthClient;
import github.salemby.tododevops.support.SharedSchemaApps;
import github.salemby.tododevops.support.TcpRelay;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static github.salemby.tododevops.support.AuthClient.PASSWORD;
import static org.junit.jupiter.api.Assertions.*;

/** Fail closed and fast when the session store is unavailable. */
class PostgresSessionRedisDownIT {
    @Test
    void requestWithASessionCookieGets503WhenRedisIsUnreachable() throws Exception {
        try (var apps = new SharedSchemaApps()) {
            int port = SharedSchemaApps.port(apps.start("spring.data.redis.port=1"));
            String cookie = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            unavailable(get(port, "/auth/me", cookie));
            assertEquals(200, get(port, "/", null).statusCode());
        }
    }

    @Test
    void droppedConnectionFailsFastInsteadOfWaitingForTheTimeout() throws Exception {
        try (var relay = new TcpRelay(6379); var apps = new SharedSchemaApps()) {
            int port = SharedSchemaApps.port(apps.start("spring.data.redis.port=" + relay.port()));
            var client = new AuthClient(port);
            assertEquals(200, client.csrf().statusCode());
            String email = UUID.randomUUID() + "@example.com";
            assertEquals(201, client.register(email).statusCode());
            assertEquals(200, client.login(email, PASSWORD).statusCode());
            relay.close();
            Thread.sleep(200); // let the client notice the dropped connection
            long started = System.nanoTime();
            var me = client.call("GET", "/auth/me", null);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            unavailable(me);
            assertTrue(elapsedMillis < 500, "503 took " + elapsedMillis + " ms; commands were queued instead of rejected");
        }
    }

    private static HttpResponse<String> get(int port, String path, String sessionCookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (sessionCookie != null) request.header("Cookie", "JSESSIONID=" + sessionCookie);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void unavailable(HttpResponse<String> response) {
        assertEquals(503, response.statusCode(), response.body());
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElse(""), response.body());
        assertTrue(response.body().contains("\"detail\":\"Authentication is temporarily unavailable.\""), response.body());
    }
}
