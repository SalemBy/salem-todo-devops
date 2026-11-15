package github.salemby.tododevops;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationStartupTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(ValidationConfigurationScan.class);

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("github.salemby.tododevops.shared.config")
    static class ValidationConfigurationScan {
    }

    @Test
    void startsWithARealValidator() {
        runner.run(context -> assertThat(context).hasSingleBean(Validator.class));
    }

    @Test
    void refusesToStartWithoutValidationProvider() throws Exception {
        String original = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        String withoutProvider = Arrays.stream(original.split(java.util.regex.Pattern.quote(File.pathSeparator)))
            .filter(entry -> !Path.of(entry).getFileName().toString().startsWith("hibernate-validator-"))
            .collect(Collectors.joining(File.pathSeparator));
        assertThat(withoutProvider).isNotEqualTo(original);
        Path output = Files.createTempFile("validation-startup-", ".log");
        Process process = null;
        try {
            // A separate JVM avoids provider classes leaking from the test JVM's parent classloader.
            process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", withoutProvider, StartupProbe.class.getName())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            String logs = Files.readString(output);
            assertThat(process.exitValue()).as(logs).isNotZero();
            assertThat(logs).contains("NoProviderFoundException");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(output);
        }
    }

    public static class StartupProbe {
        public static void main(String[] args) {
            try (var context = new AnnotationConfigApplicationContext(ValidationConfigurationScan.class)) {
                // With no provider, production validation configuration must prevent this point.
            }
        }
    }
}
