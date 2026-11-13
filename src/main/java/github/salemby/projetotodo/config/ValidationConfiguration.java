package github.salemby.projetotodo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration(proxyBeanMethods = false)
public class ValidationConfiguration {
    @Bean
    @Primary
    public LocalValidatorFactoryBean defaultValidator() {
        // Unlike MVC's optional validator, this fails startup if the provider is missing.
        return new LocalValidatorFactoryBean();
    }
}
