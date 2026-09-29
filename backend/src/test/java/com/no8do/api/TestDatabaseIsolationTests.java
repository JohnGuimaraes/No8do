package com.no8do.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TestDatabaseIsolationTests {

    private static final String LOCAL_DEVELOPMENT_URL = "jdbc:postgresql://localhost:5432/no8do";
    private static final String LOCAL_DEVELOPMENT_DATABASE = "no8do";

    @Autowired private Environment environment;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Flyway flyway;

    @Test
    void usesAnEphemeralPgvectorTestcontainerInsteadOfTheLocalDevelopmentDatabase() throws Exception {
        String configuredUrl = environment.getRequiredProperty("spring.datasource.url");

        assertThat(configuredUrl).startsWith("jdbc:tc:pgvector:pg16:///");
        assertThat(configuredUrl).isNotEqualTo(LOCAL_DEVELOPMENT_URL);
        assertThat(environment.getRequiredProperty("spring.datasource.driver-class-name"))
                .isEqualTo("org.testcontainers.jdbc.ContainerDatabaseDriver");
        try (Connection connection = dataSource.getConnection()) {
            String effectiveUrl = connection.getMetaData().getURL();
            assertThat(effectiveUrl).startsWith("jdbc:postgresql://").isNotEqualTo(LOCAL_DEVELOPMENT_URL);
        }
        assertThat(jdbcTemplate.queryForObject("select current_database()", String.class))
                .isNotEqualTo(LOCAL_DEVELOPMENT_DATABASE);
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(flyway.info().pending()).isEmpty();
        Set<String> appliedVersions = Arrays.stream(applied)
                .map(MigrationInfo::getVersion)
                .filter(java.util.Objects::nonNull)
                .map(version -> version.getVersion())
                .collect(Collectors.toSet());
        assertThat(appliedVersions).contains("38", "55");
        assertThat(jdbcTemplate.queryForObject(
                "select exists(select 1 from pg_extension where extname = 'vector')", Boolean.class)).isTrue();
    }
}
