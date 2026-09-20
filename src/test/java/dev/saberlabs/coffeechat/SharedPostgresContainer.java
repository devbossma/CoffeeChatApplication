package dev.saberlabs.coffeechat;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container for the entire test JVM, shared by every Testcontainers-backed test
 * class regardless of which Spring test slice it uses ({@code @DataJpaTest} repository tests,
 * {@code @SpringBootTest} full-context tests) &mdash; extend this instead of declaring a
 * container field locally.
 *
 * <p>Started manually in a static initializer &mdash; the "singleton container" pattern from
 * Testcontainers' own docs &mdash; rather than via {@code @Testcontainers}/{@code @Container}.
 * That JUnit5 extension's lifecycle management is documented to only reliably cover sharing
 * within a single test class; across multiple classes it was observed to stop the container as
 * soon as the first subclass's test store closed (a JUnit5/Testcontainers
 * {@code CloseableResource}-vs-{@code AutoCloseable} store-scoping incompatibility), silently
 * replacing it with a fresh container on a new port for the next class while an already-built,
 * cached Spring {@code ApplicationContext} kept the stale port &mdash; surfacing as every later
 * test timing out after HikariCP's 30s connection-acquisition wait. A manually-started container
 * with no {@code @Testcontainers}-managed stop sidesteps that store entirely; Ryuk still reaps it
 * when the JVM exits.
 *
 * <p>Sharing one container across both {@code @DataJpaTest} and {@code @SpringBootTest} classes
 * also has a second benefit beyond avoiding redundant containers: Spring's {@code TestContext}
 * cache keys an {@code ApplicationContext} by its full merged configuration, which includes the
 * {@code @ServiceConnection}-injected datasource URL. Two {@code @SpringBootTest} classes with
 * otherwise-identical annotations but <em>different</em> container instances (hence different
 * ports) used to force two separate contexts (and two separate barista thread pools each needing
 * their own shutdown); sharing this one container lets Spring's cache recognize the two as
 * identical and reuse a single context.
 */
public abstract class SharedPostgresContainer {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
