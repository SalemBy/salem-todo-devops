package github.salemby.tododevops;

import github.salemby.tododevops.support.IsolatedPostgresSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.*;
import java.net.*;
import java.net.http.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.auth.registration-enabled=false"})
@ActiveProfiles("postgres-test")
@ContextConfiguration(initializers = IsolatedPostgresSchema.class)
class PostgresRegistrationDisabledIT {
    @Autowired Environment environment;
    @Test void validRegistrationIsForbiddenWhenOnboardingDisabled() throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String base = "http://127.0.0.1:" + environment.getProperty("local.server.port");
        var token = new ObjectMapper().readTree(client.send(HttpRequest.newBuilder(URI.create(base + "/auth/csrf")).build(), HttpResponse.BodyHandlers.ofString()).body());
        var response = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/register"))
            .header("Content-Type", "application/json").header(token.get("headerName").asText(), token.get("token").asText())
            .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"closed@example.com\",\"displayName\":\"Person\",\"password\":\"A test only passphrase!\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode()); assertTrue(response.body().contains("Registration is disabled"));
    }
}
