package com.no8do.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.core.io.support.SpringFactoriesLoader;

class TestDatabaseUrlGuardTests {

    @Test
    void testOnlyInitializerIsRegisteredGloballyForSpringApplications() {
        assertThat(SpringFactoriesLoader.loadFactoryNames(ApplicationContextInitializer.class,
                getClass().getClassLoader())).contains(TestDatabaseUrlGuardInitializer.class.getName());
    }

    @Test
    void acceptsPgvectorTestcontainersUrlWithoutOpeningAConnection() {
        TestDatabaseUrlGuardInitializer.validate("jdbc:tc:pgvector:pg16:///no8do_test");
    }

    @Test
    void rejectsLocalDevelopmentPostgresUrlWithoutOpeningAConnection() {
        assertRejected("jdbc:postgresql://localhost:5432/no8do");
    }

    @Test
    void rejectsArbitraryExternalPostgresUrlWithoutOpeningAConnection() {
        assertRejected("jdbc:postgresql://db.example.invalid:5432/no8do");
    }

    @Test
    void rejectsMissingOrBlankUrlWithoutOpeningAConnection() {
        assertRejected(null);
        assertRejected("");
        assertRejected("   ");
    }

    private void assertRejected(String url) {
        assertThatThrownBy(() -> TestDatabaseUrlGuardInitializer.validate(url))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Spring tests require spring.datasource.url to use the pgvector PostgreSQL 16 Testcontainers driver.");
    }
}
