package com.regu.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Manual Flyway configuration for Spring Boot 4.
 *
 * <p>Spring Boot 4 removed built-in Flyway autoconfiguration.
 * This class replicates the essential autoconfiguration behaviour:
 * it creates a {@link Flyway} bean, runs {@code migrate()} on startup,
 * and exposes the bean so that other components can {@code @DependsOn("flyway")}
 * when they need the schema to be ready before initialising.
 *
 * <p>Settings are read from {@code spring.flyway.*} in {@code application.yml}
 * so the YAML remains the single source of truth.
 */
@Configuration
public class FlywayConfig {

    private final String locations;
    private final boolean baselineOnMigrate;
    private final String baselineVersion;
    private final boolean validateOnMigrate;

    public FlywayConfig(
            @Value("${spring.flyway.locations:classpath:db/migration}") String locations,
            @Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate,
            @Value("${spring.flyway.baseline-version:0}") String baselineVersion,
            @Value("${spring.flyway.validate-on-migrate:true}") boolean validateOnMigrate) {
        this.locations = locations;
        this.baselineOnMigrate = baselineOnMigrate;
        this.baselineVersion = baselineVersion;
        this.validateOnMigrate = validateOnMigrate;
    }

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .validateOnMigrate(validateOnMigrate)
                .load();
        flyway.migrate();
        return flyway;
    }
}
