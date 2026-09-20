package dev.saberlabs.coffeechat;

import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test: the Spring context has to load cleanly before anything else matters. Shares the
 * suite's one full context and one Postgres container via {@link AbstractIntegrationTest}.
 */
class CoffeeChatApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
