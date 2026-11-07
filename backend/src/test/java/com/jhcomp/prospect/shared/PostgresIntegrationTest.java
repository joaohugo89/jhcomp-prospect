package com.jhcomp.prospect.shared;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The base of tests that need a real PostgreSQL (doc 10 §2).
 *
 * <p>It uses the singleton container pattern: the container starts once per
 * JVM in a static block and is never stopped explicitly. Do not use
 * {@code @Testcontainers} with {@code @Container} here — that extension stops
 * the container at the end of each test class, while Spring reuses the cached
 * context pointing at the now-dead container, which makes the following
 * classes fail with "Failed to obtain JDBC Connection".
 *
 * <p>Shutdown is Ryuk's job, the sidecar container Testcontainers starts to
 * remove resources at the end of the run.
 *
 * <p>The class comes from {@code org.testcontainers.postgresql}, not from
 * {@code org.testcontainers.containers}, which was deprecated in
 * Testcontainers 2.x. The new class is not generic, so it takes no
 * {@code <?>}.
 *
 * <p>The image matches the one in {@code docker-compose.yml}: testing against
 * a different version from the development one hides migration problems.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES;

    static {
        // The container deliberately lives for the whole test JVM, so it is
        // not closed here. Closing it would invalidate the cached Spring
        // context.
        @SuppressWarnings("resource")
        PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
                .withDatabaseName("jhcomp_prospect_test")
                .withUsername("jhcomp_test")
                .withPassword("jhcomp_test");

        container
                // RNF-009: the test database runs in UTC, as production does.
                .withEnv("TZ", "UTC")
                .withEnv("PGTZ", "UTC")
                // The same collation as docker-compose, which deduplication depends on.
                .withEnv("POSTGRES_INITDB_ARGS", "--encoding=UTF8 --locale=C");

        POSTGRES = container;
        POSTGRES.start();
    }
}
