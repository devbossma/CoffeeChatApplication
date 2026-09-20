package dev.saberlabs.coffeechat.repository;

import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Shared base for every {@code @DataJpaTest} repository test: one {@code @ServiceConnection}
 * Testcontainers Postgres, declared once. Every subclass sees byte-identical test configuration,
 * so Spring's {@code TestContext} cache reuses a single context (and a single container) across
 * all of them instead of spinning up one per class &mdash; the exact "keep the number of distinct
 * Spring test contexts small" lesson Part 02's shutdown-stall investigation left behind (that was
 * about full {@code @SpringBootTest} contexts each starting a barista pool; a {@code @DataJpaTest}
 * slice never loads {@code BaristaSupervisor} at all, so the stall mechanism itself doesn't apply
 * here, but avoiding redundant containers is still the right default).
 *
 * <p>Started manually in a static initializer &mdash; the "singleton container" pattern from
 * Testcontainers' own docs &mdash; rather than via {@code @Testcontainers}/{@code @Container}.
 * That JUnit5 extension's lifecycle management is documented to only reliably cover sharing
 * within a single test class; across the six concrete subclasses here, it was observed to stop
 * the container as soon as the first subclass's test store closed (a JUnit5/Testcontainers
 * {@code CloseableResource}-vs-{@code AutoCloseable} store-scoping incompatibility), silently
 * replacing it with a fresh container on a new port for the next class while the already-built,
 * cached Spring {@code ApplicationContext} kept the stale port &mdash; surfacing as every later
 * test timing out after HikariCP's 30s connection-acquisition wait. A manually-started container
 * with no {@code @Testcontainers}-managed stop sidesteps that store entirely; Ryuk still reaps it
 * when the JVM exits.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} is required: {@code @DataJpaTest}
 * normally swaps in an embedded database, which would both bypass the real Postgres container
 * and skip the Flyway-owned schema entirely.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class AbstractRepositoryTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private DataSource dataSource;

    /**
     * Proves a DB-level constraint (NOT NULL / CHECK / FK / UNIQUE) independently of JPA:
     * borrows its own raw JDBC connection from the pool, runs {@code sql}, asserts it throws
     * {@link SQLException}, and returns the connection via try-with-resources regardless of
     * outcome.
     *
     * <p>Deliberately <em>not</em> routed through {@code TestEntityManager}'s JPA
     * {@code EntityManager}: a caught constraint violation marks that Hibernate session
     * rollback-only per the JPA spec, and running these checks through the same
     * session/transaction other tests in the class share was found to leak a pooled connection
     * (surfacing as a 30s {@code CannotCreateTransactionException} timeout on later tests once
     * the pool was exhausted) &mdash; a fully independent, immediately-closed connection sidesteps
     * that entanglement entirely.
     */
    protected void assertConstraintViolation(String sql) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            Executable attempt = () -> statement.executeUpdate(sql);
            assertThrows(SQLException.class, attempt);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not obtain a JDBC connection for the constraint check", e);
        }
    }
}
