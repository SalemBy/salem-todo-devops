package github.salemby.tododevops.support;

import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Creates one schema per cached Spring context; drops it after its beans are destroyed. */
public final class IsolatedPostgresSchema
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String url = environment.getRequiredProperty("spring.datasource.url");
        var credentials = new Properties();
        credentials.setProperty("user", environment.getRequiredProperty("spring.datasource.username"));
        credentials.setProperty("password", environment.getRequiredProperty("spring.datasource.password"));
        credentials.setProperty("connectTimeout", "5");
        credentials.setProperty("socketTimeout", "15");
        credentials.setProperty("options", "-c statement_timeout=10000");
        // Generated identifiers only: neither creation nor cleanup can target a supplied schema.
        String schema = "http_test_" + UUID.randomUUID().toString().replace("-", "");
        execute(url, credentials, "CREATE SCHEMA " + schema);
        var beans = (DefaultSingletonBeanRegistry) context.getBeanFactory();
        beans.registerSingleton("isolatedPostgresSchema", schema);
        beans.registerDisposableBean("isolatedPostgresSchema",
            () -> execute(url, credentials, "DROP SCHEMA " + schema + " CASCADE"));
        environment.getPropertySources().addFirst(new MapPropertySource("isolatedPostgresSchema", Map.of(
            "spring.flyway.schemas", schema,
            "spring.flyway.default-schema", schema,
            "spring.jpa.properties.hibernate.default_schema", schema,
            "spring.datasource.hikari.schema", schema)));
    }

    private static void execute(String url, Properties credentials, String sql) {
        try (Connection connection = DriverManager.getConnection(url, credentials);
             var statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot manage isolated PostgreSQL test schema", exception);
        }
    }
}
