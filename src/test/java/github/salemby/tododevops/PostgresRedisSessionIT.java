package github.salemby.tododevops;

import github.salemby.tododevops.support.AuthClient;
import github.salemby.tododevops.support.SharedSchemaApps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static github.salemby.tododevops.support.AuthClient.PASSWORD;
import static org.junit.jupiter.api.Assertions.*;

/** Two instances share one schema and the Compose Redis, like servers behind a load balancer. */
class PostgresRedisSessionIT {
    static SharedSchemaApps apps;
    static ConfigurableApplicationContext a, b;

    @BeforeAll static void start() { apps = new SharedSchemaApps(); a = apps.start(); b = apps.start(); }
    @AfterAll static void stop() { apps.close(); }

    static int portA() { return SharedSchemaApps.port(a); }
    static int portB() { return SharedSchemaApps.port(b); }

    /** Registers a fresh account on the given server and returns a client logged in there. */
    static AuthClient loggedIn(int port, String email) throws Exception {
        var client = new AuthClient(port);
        assertEquals(200, client.csrf().statusCode());
        assertEquals(201, client.register(email).statusCode());
        assertEquals(200, client.login(email, PASSWORD).statusCode());
        return client;
    }

    static String email() { return UUID.randomUUID() + "@example.com"; }

    static HttpResponse<String> meWithCookie(int port, String sessionCookie) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/me"))
            .header("Cookie", "JSESSIONID=" + sessionCookie).timeout(Duration.ofSeconds(15)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void sessionSurvivesARestart() throws Exception {
        try (var restartable = new SharedSchemaApps()) {
            var first = restartable.start();
            String email = email();
            String cookie = loggedIn(SharedSchemaApps.port(first), email).cookie("JSESSIONID");
            first.close();
            var second = restartable.start();
            var me = meWithCookie(SharedSchemaApps.port(second), cookie);
            assertEquals(200, me.statusCode(), me.body());
            assertTrue(me.body().contains(email), me.body());
        }
    }

    @Test
    void sessionIsSharedBetweenServers() throws Exception {
        String email = email();
        var onA = loggedIn(portA(), email);
        var onB = new AuthClient(portB(), onA.cookies());
        var me = onB.call("GET", "/auth/me", null);
        assertEquals(200, me.statusCode(), me.body());
        assertTrue(me.body().contains(email), me.body());
    }

    @Test
    void logoutOnOneServerIsEnforcedOnTheOther() throws Exception {
        var onA = loggedIn(portA(), email());
        String cookie = onA.cookie("JSESSIONID");
        assertEquals(200, onA.csrf().statusCode());
        assertEquals(204, onA.call("POST", "/auth/logout", null).statusCode());
        assertEquals(401, meWithCookie(portB(), cookie).statusCode());
    }

    @Test
    void passwordChangeOnOneServerEndsSessionsOnTheOther() throws Exception {
        String email = email();
        var first = loggedIn(portA(), email);
        var second = new AuthClient(portB());
        assertEquals(200, second.csrf().statusCode());
        assertEquals(200, second.login(email, PASSWORD).statusCode());
        assertEquals(200, first.csrf().statusCode());
        assertEquals(204, first.call("POST", "/auth/password",
            Map.of("currentPassword", PASSWORD, "newPassword", "Another test only passphrase!")).statusCode());
        assertEquals(401, second.call("GET", "/auth/me", null).statusCode());
    }

    @Test
    void noPasswordOrHashIsStoredInRedis() throws Exception {
        String email = email();
        loggedIn(portA(), email);
        String hash = a.getBean(JdbcTemplate.class).queryForObject("select password_hash from accounts where email = ?", String.class, email);
        int sessions = 0;
        try (var redis = a.getBean(RedisConnectionFactory.class).getConnection()) {
            for (byte[] key : redis.keyCommands().keys("spring:session:sessions:*".getBytes(StandardCharsets.UTF_8))) {
                sessions++;
                for (var entry : redis.hashCommands().hGetAll(key).entrySet()) {
                    String stored = new String(entry.getValue(), StandardCharsets.ISO_8859_1);
                    assertFalse(stored.contains(PASSWORD), "password stored in " + new String(entry.getKey()));
                    assertFalse(stored.contains(hash), "password hash stored in " + new String(entry.getKey()));
                    assertFalse(stored.contains("{pbkdf2"), "a password hash stored in " + new String(entry.getKey()));
                }
            }
        }
        assertTrue(sessions > 0, "no session was stored in Redis");
    }

    @Test
    void loginCookieKeepsItsAttributes() throws Exception {
        var client = new AuthClient(portA());
        assertEquals(200, client.csrf().statusCode());
        String email = email();
        assertEquals(201, client.register(email).statusCode());
        var login = client.login(email, PASSWORD);
        String cookie = login.headers().allValues("Set-Cookie").stream().filter(v -> v.startsWith("JSESSIONID=")).findFirst()
            .orElseThrow(() -> new AssertionError("no JSESSIONID cookie in " + login.headers().allValues("Set-Cookie")));
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
        assertTrue(cookie.contains("Path=/"), cookie);
    }

    @Test
    void logoutClearsTheCookieOnce() throws Exception {
        var client = loggedIn(portA(), email());
        assertEquals(200, client.csrf().statusCode());
        var logout = client.call("POST", "/auth/logout", null);
        var cleared = logout.headers().allValues("Set-Cookie").stream().filter(v -> v.startsWith("JSESSIONID=")).toList();
        assertEquals(1, cleared.size(), cleared.toString());
        assertTrue(cleared.get(0).contains("Max-Age=0"), cleared.get(0));
    }
}
