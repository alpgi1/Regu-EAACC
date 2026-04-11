package com.regu.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures Flyway migrations run before Hibernate validates the schema.
 *
 * <p>Spring Boot 4 removed Flyway autoconfiguration, so the built-in
 * ordering guarantee is gone. On a cold-start empty database, Hibernate's
 * ddl-auto=validate fires before our manual {@link FlywayConfig#flyway}
 * bean runs and creates the tables — causing a "missing table" error.
 *
 * <p>This {@code BeanFactoryPostProcessor} adds "flyway" to the
 * {@code dependsOn} list of the JPA {@code entityManagerFactory} bean,
 * restoring the correct ordering at the bean-definition level.
 *
 * <p>The {@code @Bean} method is {@code static} so Spring can invoke it
 * without instantiating this {@code @Configuration} class first — required
 * for {@code BeanFactoryPostProcessor} beans to fire early enough.
 */
@Configuration
public class JpaOrderingConfig {

    @Bean
    public static BeanFactoryPostProcessor jpaAfterFlywayPostProcessor() {
        return (ConfigurableListableBeanFactory beanFactory) -> {
            BeanDefinition emf = beanFactory.getBeanDefinition("entityManagerFactory");
            emf.setDependsOn("flyway");
        };
    }
}
