package com.no8do.api;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Rejects a non-Testcontainers datasource before Spring creates DataSource or Flyway beans. */
public final class TestDatabaseUrlGuardInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String REQUIRED_PREFIX = "jdbc:tc:pgvector:pg16:///";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        validate(applicationContext.getEnvironment().getProperty("spring.datasource.url"));
    }

    static void validate(String url) {
        if (url == null || !url.startsWith(REQUIRED_PREFIX)) {
            throw new IllegalStateException(
                    "Spring tests require spring.datasource.url to use the pgvector PostgreSQL 16 Testcontainers driver.");
        }
    }
}
