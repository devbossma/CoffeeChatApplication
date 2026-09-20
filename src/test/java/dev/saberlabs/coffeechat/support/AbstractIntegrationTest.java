package dev.saberlabs.coffeechat.support;

import dev.saberlabs.coffeechat.service.StaffService;
import dev.saberlabs.coffeechat.service.StaffAccess;
import dev.saberlabs.coffeechat.SharedPostgresContainer;
import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.command.OrderInvoker;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.factory.CoffeeFactory;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.prototype.OrderPrototype;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.service.PaymentService;
import dev.saberlabs.coffeechat.strategy.PricingStrategyResolver;
import dev.saberlabs.coffeechat.template.CoffeePreparationResolver;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.chat.BaristaQueue;
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
import org.springframework.context.ApplicationContext;
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
 * <p>The shared context starts with live barista consumer loops that would race a test which seeds
 * a {@code PLACED} order and expects to observe it. So the baristas are kept <em>stopped between
 * tests</em>: the first test stops them (a no-op afterwards, since stopping an already-stopped
 * supervisor costs nothing), and only a test that overrides {@link #baristasLive()} starts them, for
 * itself, and they are stopped again in a {@code finally} after it.
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
    @Autowired protected BaristaQueue baristaQueue;
    @Autowired protected OrderNotificationListener notifications;
    @Autowired protected CoffeeShop coffeeShop;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired private ApplicationContext context;
    @Autowired private BaristaSupervisor supervisor;
    @Autowired @Qualifier("baristaTaskExecutor") private ThreadPoolTaskExecutor baristaExecutor;

    /**
     * A real facade wired from the context's real beans, except for the payment gateways: for tests
     * that need a payment to decline or need to count how often the gateway is really called.
     */
    protected CoffeeShopFacade facadeWith(PaymentGatewayResolver gateways) {
        return new CoffeeShopFacade(
                context.getBean(CoffeeShop.class),
                context.getBean(CoffeeFactory.class),
                context.getBean(PricingStrategyResolver.class),
                context.getBean(CoffeePreparationResolver.class),
                gateways,
                context.getBean(OrderService.class),
                context.getBean(PaymentService.class),
                context.getBean(StaffAccess.class),
                context.getBean(CustomerService.class),
                users,
                context.getBean(OrderEventPublisher.class),
                context.getBean(OrderInvoker.class),
                context.getBeanProvider(OrderPrototype.class));
    }

    /**
     * Override to {@code true} for a test that exercises the real barista pipeline: the baristas are
     * started for it and stopped again afterwards. Everything else runs with the baristas stopped.
     */
    protected boolean baristasLive() {
        return false;
    }

    @BeforeEach
    void resetSharedState() throws InterruptedException {
        stopBaristasAndAwaitIdle();
        cleanSharedState();
        if (baristasLive()) {
            supervisor.start();
        }
    }

    /**
     * Cleans up AFTER the test as well as before it: these tests commit real rows into the one
     * Postgres that every other test class (including the {@code @DataJpaTest} repository tests)
     * shares, so anything left behind would break an unrelated class. The baristas are stopped in a
     * {@code finally}, so a failing live-barista test can never leave the shared context with
     * running baristas that act on the next test's rows.
     */
    @AfterEach
    void cleanupAndStopBaristas() throws InterruptedException {
        try {
            stopBaristasAndAwaitIdle();
            cleanSharedState();
        } finally {
            supervisor.stop();
        }
    }

    /** Assumes the baristas are stopped and idle. */
    private void cleanSharedState() throws InterruptedException {
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
        baristaQueue.clear();
        coffeeShop.reset();
    }

    /**
     * Stops the supervisor and waits for the loops to go idle, but only if there is something to
     * stop: between tests the baristas are already stopped, so this is then free.
     */
    protected void stopBaristasAndAwaitIdle() {
        if (supervisor.isRunning() || baristaExecutor.getActiveCount() > 0) {
            supervisor.stop();
            await().atMost(Duration.ofSeconds(5)).until(() -> baristaExecutor.getActiveCount() == 0);
        }
    }

    /** A CUSTOMER with {@code fulfilledOrders} already recorded (set directly: fixtures, not behaviour). */
    protected UserEntity customer(String name, long fulfilledOrders) {
        UserEntity saved = users.save(new UserEntity(name, Role.CUSTOMER));
        if (fulfilledOrders > 0) {
            jdbc.update("UPDATE user_accounts SET fulfilled_orders = ? WHERE id = ?", fulfilledOrders, saved.id());
        }
        return saved;
    }

    protected UserEntity barista(String name) {
        return context.getBean(StaffService.class).createBarista(name);
    }

    protected UserEntity manager(String name) {
        return context.getBean(StaffService.class).createManager(name);
    }

    protected UserEntity customer(String name) {
        return customer(name, 0);
    }

    protected long fulfilledOrdersOf(Long userId) {
        return jdbc.queryForObject("SELECT fulfilled_orders FROM user_accounts WHERE id = ?", Long.class, userId);
    }
}
