# Test database isolation

Development continues to use the local `no8do` database. Spring Boot tests use an ephemeral pgvector PostgreSQL 16 Testcontainers database instead.

Docker is required for database-backed tests. If Docker or Testcontainers is unavailable, those tests fail; they never fall back to the local development datasource. A test-classpath `ApplicationContextInitializer` rejects any overridden datasource URL that does not use the required pgvector Testcontainers JDBC prefix before Spring refreshes the context. Containers are disposable and do not use reusable-container settings or persistent volumes.

The historical local fixture was not removed. This configuration prevents new test executions from writing to the local development database.
