package dev.saberlabs.coffeechat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: the Spring context has to load cleanly before anything else matters.
 *
 * <p>Runs against a real, disposable Postgres container instead of {@code application.properties}'
 * local database, via Testcontainers' {@code @ServiceConnection} -- Spring Boot wires the
 * datasource to the container automatically, no manual property overrides needed. This is the
 * same mechanism the real persistence/integration tests will use once entities exist
 * (see PRD.md section 10). Requires Docker to be running locally.
 */
@SpringBootTest
@Testcontainers
class CoffeeChatApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void contextLoads() {
    }
}
