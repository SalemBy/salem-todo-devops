package github.salemby.tododevops.support;

import github.salemby.tododevops.SalemTodoDevOpsApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Several application instances on one PostgreSQL schema and the shared Redis, the way
 * servers behind a load balancer run. Each start is a separate Spring context on its own port.
 */
public final class SharedSchemaApps implements AutoCloseable {
    private final String url = System.getenv("TEST_DB_URL");
    private final String user = System.getenv("TEST_DB_USERNAME");
    private final String password = System.getenv("TEST_DB_PASSWORD");
    // Generated identifiers only: neither creation nor cleanup can target a supplied schema.
    private final String schema = "shared_test_" + UUID.randomUUID().toString().replace("-", "");
    private final List<ConfigurableApplicationContext> started = new ArrayList<>();

    public SharedSchemaApps() {
        execute("CREATE SCHEMA " + schema);
    }

    public ConfigurableApplicationContext start(String... extraProperties) {
        var properties = new ArrayList<>(List.of("server.port=0",
            "spring.flyway.schemas=" + schema, "spring.flyway.default-schema=" + schema,
            "spring.jpa.properties.hibernate.default_schema=" + schema, "spring.datasource.hikari.schema=" + schema));
        properties.addAll(List.of(extraProperties));
        // Command-line arguments, because builder properties are defaults that application.properties overrides.
        var context = new SpringApplicationBuilder(SalemTodoDevOpsApplication.class).profiles("postgres-test")
            .run(properties.stream().map(property -> "--" + property).toArray(String[]::new));
        started.add(context);
        return context;
    }

    public static int port(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
    }

    @Override
    public void close() {
        for (var context : started) context.close();
        execute("DROP SCHEMA " + schema + " CASCADE");
    }

    private void execute(String sql) {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot manage shared PostgreSQL test schema", exception);
        }
    }
}
