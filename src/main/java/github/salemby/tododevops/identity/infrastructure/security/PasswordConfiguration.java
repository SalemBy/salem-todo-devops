package github.salemby.tododevops.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.*;
import java.util.Map;

@Configuration
public class PasswordConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() {
        String id = "pbkdf2-sha256-600k-v1";
        var encoder = new Pbkdf2PasswordEncoder("", 16, 600_000,
            Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        return new DelegatingPasswordEncoder(id, Map.of(id, encoder));
    }
}
