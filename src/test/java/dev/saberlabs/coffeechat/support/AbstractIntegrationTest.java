package dev.saberlabs.coffeechat.support;

import dev.saberlabs.coffeechat.SharedPostgresContainer;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.multithread.BaristaSupervisor;
import dev.saberlabs.coffeechat.multithread.OrderQueue;
import dev.saberlabs.coffeechat.observer.OrderNotificationListener;
import dev.saberlabs.coffeechat.repository.ChatMessageRepository;
import dev.saberlabs.coffeechat.repository.ChatSessionRepository;
import dev.saberlabs.coffeechat.repository.OrderRepository;
import dev.saberlabs.coffeechat.repository.OrderStatusHistoryRepository;
import dev.saberlabs.coffeechat.repository.PaymentRepository;
import dev.saberlabs.coffeechat.repository.UserRepository;
import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * Base for every test that needs the real application: one full Spring context (shared with every
 * other subclass through Spring's context cache, on the one {@link SharedPostgresContainer}),
 * real commits, real {@code AFTER_COMMIT} listeners. Tests here are deliberately <b>not</b>
 * {@code @Transactional}: the behaviours under test (commit-then-enqueue, optimistic-lock races,
 * two threads on one row) only exist across real transaction boundaries.
 *
 * <p>The shared context has live barista consumer loops that would race a test which seeds a
 * {@code PLACED} order and expects to observe it. So by default the baristas are stopped (and
 * awaited idle) before each test and unconditionally restored after it, in {@link #restoreBaristas}
 * &mdash; a failing test can never leave the shared context with stopped baristas for the next class.
 * A test that wants the real pipeline overrides {@link #baristasLive()}.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest extends SharedPostgresContainer {

    @Autowired protected UserRepository users;
    @Autowired protected OrderRepository orders;
    @Autowired protected PaymentRepository payments;
    @Autowired protected OrderStatusHistoryRepository history;
    @Autowired protected ChatMessageRepository chatMessages;
    @Autowired protected ChatSessionRepository chatSessions;
    @Autowired protected OrderQueue orderQueue;
    @Autowired protected OrderNotificationListener notifications;
    @Autowired protected CoffeeShop coffeeShop;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired private BaristaSupervisor supervisor;
    @Autowired @Qualifier("baristaTaskExecutor") private ThreadPoolTaskExecutor baristaExecutor;

    /** Override to {@code true} for a test that exercises the real barista pipeline. */
    protected boolean baristasLive() {
        return false;
    }

    @BeforeEach
    void resetSharedState() throws InterruptedException {
        cleanSharedState();
        if (baristasLive()) {
            supervisor.start();
        }
    }

    /**
     * Cleans up AFTER the test as well as before it: these tests commit real rows into the one
     * Postgres that every other test class (including the {@code @DataJpaTest} repository tests)
     * shares, so anything left behind would break an unrelated class. The baristas are restored last,
     * in every case, so a failing test never leaves the shared context with stopped baristas.
     */
    @AfterEach
    void cleanupAndRestoreBaristas() throws InterruptedException {
        try {
            cleanSharedState();
        } finally {
            supervisor.start();
        }
    }

    private void cleanSharedState() throws InterruptedException {
        stopBaristasAndAwaitIdle();
        chatMessages.deleteAllInBatch();
        chatSessions.deleteAllInBatch();
        history.deleteAllInBatch();
        payments.deleteAllInBatch();
        orders.deleteAllInBatch();
        users.deleteAllInBatch();
        while (orderQueue.poll(0, TimeUnit.MILLISECONDS) != null) {
            // drain ids left behind by the test
        }
        notifications.clear();
        coffeeShop.reset();
    }

    protected void stopBaristasAndAwaitIdle() {
        supervisor.stop();
        await().atMost(Duration.ofSeconds(5)).until(() -> baristaExecutor.getActiveCount() == 0);
    }

    /** A CUSTOMER with {@code fulfilledOrders} already recorded (set directly: fixtures, not behaviour). */
    protected UserEntity customer(String name, long fulfilledOrders) {
        UserEntity saved = users.save(new UserEntity(name, Role.CUSTOMER));
        if (fulfilledOrders > 0) {
            jdbc.update("UPDATE user_accounts SET fulfilled_orders = ? WHERE id = ?", fulfilledOrders, saved.id());
        }
        return saved;
    }

    protected UserEntity customer(String name) {
        return customer(name, 0);
    }

    protected long fulfilledOrdersOf(Long userId) {
        return jdbc.queryForObject("SELECT fulfilled_orders FROM user_accounts WHERE id = ?", Long.class, userId);
    }
}
