package dev.saberlabs.coffeechat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring context has to load cleanly before anything else matters.
 *
 * <p>Runs against the suite-wide singleton Postgres container from {@link SharedPostgresContainer}
 * (wired via {@code @ServiceConnection}), not a container of its own -- identical configuration to
 * {@code BaristaIntegrationTest}, so Spring's context cache reuses one full context for both.
 */
@SpringBootTest
class CoffeeChatApplicationTests extends SharedPostgresContainer {

    @Test
    void contextLoads() {
    }
}
