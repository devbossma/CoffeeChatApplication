package dev.saberlabs.coffeechat.e2e;

import com.jayway.jsonpath.JsonPath;
import dev.saberlabs.coffeechat.adapter.PayPalAdapter;
import dev.saberlabs.coffeechat.adapter.PayPalPaymentService;
import dev.saberlabs.coffeechat.adapter.PaymentGateway;
import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.multithread.OrderRecovery;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Part 03 end to end: everything is driven over HTTP through the real controllers, the real facade, the real
 * asynchronous baristas and a real Postgres, in the ONE full application context (MockMvc is part of that
 * context, so this adds no Spring context). Every flow asserts the HTTP answers <em>and</em> the rows they
 * left in the database. Asynchronous effects are awaited (Awaitility), never slept on.
 *
 * <p>{@link #baristasLive()} starts the real barista loops for each test and the base class stops them again in
 * a {@code finally}; the base class also cleans the database before and after every test, so nothing here
 * depends on another test.
 */
@DisplayName("End to end over HTTP")
class EndToEndFlowsTest extends AbstractIntegrationTest {

    private static final Duration ASYNC = Duration.ofSeconds(15);

    @Autowired MockMvc mvc;
    @Autowired OrderRecovery orderRecovery;
    @Autowired PaymentGatewayResolver gateways;

    @Override
    protected boolean baristasLive() {
        return true;
    }

    // ---------------------------------------------------------------- helpers

    private MvcResult call(HttpMethod method, String path, Long actor, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path);
        if (actor != null) {
            builder.header("X-User-Id", actor);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(builder).andReturn();
    }

    private MvcResult post(String path, Long actor, String body) throws Exception {
        return call(HttpMethod.POST, path, actor, body);
    }

    private MvcResult get(String path, Long actor) throws Exception {
        return call(HttpMethod.GET, path, actor, null);
    }

    private static int statusOf(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private static String bodyOf(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    private static <T> T json(MvcResult r, String path) throws Exception {
        return JsonPath.read(bodyOf(r), path);
    }

    private static long idOf(MvcResult r) throws Exception {
        return ((Number) json(r, "$.id")).longValue();
    }

    private long newCustomer(String name) throws Exception {
        MvcResult r = post("/api/customers", null, "{\"name\":\"" + name + "\"}");
        assertEquals(201, statusOf(r));
        return idOf(r);
    }

    private long newStaff(UserEntity manager, String name, String role) throws Exception {
        MvcResult r = post("/api/staff", manager.id(), "{\"name\":\"" + name + "\",\"role\":\"" + role + "\"}");
        assertEquals(201, statusOf(r), bodyOf(r));
        return idOf(r);
    }

    private long placeOrder(long customer, String type, String extrasJson) throws Exception {
        MvcResult r = post("/api/orders", customer,
                "{\"customerId\":" + customer + ",\"type\":\"" + type + "\",\"extras\":" + extrasJson + "}");
        assertEquals(201, statusOf(r), bodyOf(r));
        return idOf(r);
    }

    private String dbStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private void awaitStatus(long orderId, String status) {
        await().atMost(ASYNC).until(() -> status.equals(dbStatus(orderId)));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long fulfilled(long userId) {
        return fulfilledOrdersOf(userId);
    }

    /** Place, let the real baristas prepare it, pay cash as the customer, fulfil as the barista. */
    private long completeOrder(long customer, long barista, String type, String extras) throws Exception {
        long order = placeOrder(customer, type, extras);
        awaitStatus(order, "READY");
        assertEquals(200, statusOf(post("/api/orders/" + order + "/pay", customer, "{\"provider\":\"CASH\"}")));
        assertEquals(200, statusOf(post("/api/orders/" + order + "/fulfil", barista, null)));
        return order;
    }

    private <T> List<T> concurrently(List<Callable<T>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> job : jobs) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return job.call();
                }));
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Swaps one gateway of the real resolver for the duration of a test; restores it in {@code finally}. */
    @SuppressWarnings("unchecked")
    private void withGateway(PaymentProvider provider, PaymentGateway replacement, ThrowingRunnable test) throws Exception {
        Field field = PaymentGatewayResolver.class.getDeclaredField("byProvider");
        field.setAccessible(true);
        Map<PaymentProvider, PaymentGateway> map = (EnumMap<PaymentProvider, PaymentGateway>) field.get(gateways);
        PaymentGateway original = map.get(provider);
        map.put(provider, replacement);
        try {
            test.run();
        } finally {
            map.put(provider, original);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    // ---------------------------------------------------------------- a. order lifecycle

    @Nested
    @DisplayName("a. order lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("manager creates a barista, customer orders, the async barista prepares, customer pays, staff fulfils")
        void fullLifecycle() throws Exception {
            UserEntity manager = manager("Boss");
            long barista = newStaff(manager, "Bob", "BARISTA");
            long customer = newCustomer("Alice");
            assertEquals("BARISTA", jdbc.queryForObject("SELECT role FROM user_accounts WHERE id = ?", String.class, barista));

            long order = placeOrder(customer, "LATTE", "[\"MILK\",\"SUGAR\"]");
            assertEquals(List.of("MILK", "SUGAR"),
                    jdbc.queryForList("SELECT extra_type FROM order_extras WHERE order_id = ? ORDER BY extra_index", String.class, order));

            awaitStatus(order, "READY");
            assertEquals("READY", JsonPath.read(bodyOf(get("/api/orders/" + order, customer)), "$.status"));

            MvcResult paid = post("/api/orders/" + order + "/pay", customer, "{\"provider\":\"STRIPE\"}");
            assertEquals(200, statusOf(paid));
            assertEquals("PAID", json(paid, "$.status"));
            assertEquals(1, count("SELECT count(*) FROM payments WHERE order_id = ? AND status = 'PAID' AND provider = 'STRIPE'", order));
            assertTrue(jdbc.queryForObject(
                    "SELECT p.amount = o.price_total FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.id = ?", Boolean.class, order));
            assertEquals(0, fulfilled(customer));

            MvcResult fulfilledResult = post("/api/orders/" + order + "/fulfil", barista, null);
            assertEquals(200, statusOf(fulfilledResult));
            assertEquals("FULFILLED", json(fulfilledResult, "$.status"));
            assertEquals("FULFILLED", dbStatus(order));
            assertEquals(1, fulfilled(customer), "fulfilled_orders is incremented exactly once");

            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT from_status, to_status, changed_by FROM order_status_history WHERE order_id = ? ORDER BY id", order);
            assertNull(rows.get(0).get("from_status"));
            assertEquals("PLACED", rows.get(0).get("to_status"));
            for (int i = 1; i < rows.size(); i++) {
                assertEquals(rows.get(i - 1).get("to_status"), rows.get(i).get("from_status"), "history is continuous at row " + i);
            }
            Map<String, Object> last = rows.get(rows.size() - 1);
            assertEquals("FULFILLED", last.get("to_status"));
            assertEquals(barista, ((Number) last.get("changed_by")).longValue(), "the fulfilment is attributed to the barista");
            for (Map<String, Object> row : rows.subList(0, rows.size() - 1)) {
                assertNull(row.get("changed_by"), "the automatic steps are system rows");
            }
        }
    }

    // ---------------------------------------------------------------- b. chat flow

    @Nested
    @DisplayName("b. chat flow")
    class ChatFlowTests {

        @Test
        @DisplayName("match, /order via chat with a persisted confirmation, paged history, end and rematch")
        void chatFlow() throws Exception {
            UserEntity manager = manager("Boss");
            long bob = newStaff(manager, "Bob", "BARISTA");
            long alice = newCustomer("Alice");
            long carl = newCustomer("Carl");

            assertEquals(204, statusOf(post("/api/chat/barista/ready", bob, null)));
            MvcResult started = post("/api/chat/sessions", alice, null);
            assertEquals(201, statusOf(started));
            assertEquals("ACTIVE", json(started, "$.status"));
            long session = idOf(started);
            assertEquals(bob, ((Number) json(started, "$.baristaId")).longValue());

            MvcResult sent = post("/api/chat/sessions/" + session + "/messages", alice, "{\"content\":\"/order latte milk\"}");
            assertEquals(201, statusOf(sent));
            long order = ((Number) json(sent, "$.orderId")).longValue();
            assertEquals(order, ((Number) json(sent, "$.reply.orderId")).longValue());
            assertEquals("SYSTEM_MESSAGE", json(sent, "$.reply.type"));

            assertEquals(1, count("SELECT count(*) FROM chat_messages WHERE session_id = ? AND type = 'CHAT_MESSAGE'", session));
            assertEquals(order, count("SELECT order_id FROM chat_messages WHERE session_id = ? AND type = 'SYSTEM_MESSAGE' AND order_id IS NOT NULL", session));
            assertEquals(alice, count("SELECT customer_id FROM orders WHERE id = ?", order));
            awaitStatus(order, "READY");

            List<String> all = jdbc.queryForList("SELECT content FROM chat_messages WHERE session_id = ? ORDER BY sent_at, id", String.class, session);
            assertEquals(3, all.size());
            MvcResult page0 = get("/api/chat/sessions/" + session + "/messages?page=0&size=2", alice);
            MvcResult page1 = get("/api/chat/sessions/" + session + "/messages?page=1&size=2", bob);
            assertEquals(all.subList(0, 2), JsonPath.read(bodyOf(page0), "$.messages[*].content"));
            assertEquals(all.subList(2, 3), JsonPath.read(bodyOf(page1), "$.messages[*].content"));
            assertEquals(200, statusOf(get("/api/chat/sessions/" + session + "/messages", alice)));

            MvcResult waiting = post("/api/chat/sessions", carl, null);
            assertEquals("WAITING", json(waiting, "$.status"));
            long second = idOf(waiting);

            MvcResult ended = post("/api/chat/sessions/" + session + "/end", bob, null);
            assertEquals(200, statusOf(ended));
            assertEquals(true, json(ended, "$.ended"));
            assertEquals("INACTIVE", jdbc.queryForObject("SELECT status FROM chat_sessions WHERE id = ?", String.class, session));

            assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM chat_sessions WHERE id = ?", String.class, second));
            assertEquals(bob, count("SELECT barista_id FROM chat_sessions WHERE id = ?", second));
            MvcResult mine = get("/api/chat/sessions/mine", bob);
            assertEquals(second, ((Number) json(mine, "$.id")).longValue());
            assertEquals("Carl", json(mine, "$.customerName"));
            assertEquals(1, count("SELECT count(*) FROM chat_messages WHERE session_id = ? AND content = 'Bob joined the chat'", second));
        }
    }

    // ---------------------------------------------------------------- c. reorder

    @Nested
    @DisplayName("c. reorder")
    class ReorderTests {

        @Test
        @DisplayName("a [MILK, MILK, SUGAR] order is cloned from the database with a new id and the customer's CURRENT tier")
        void reorderKeepsDuplicateExtrasAndUsesCurrentTier() throws Exception {
            long customer = newCustomer("Alice");
            long original = placeOrder(customer, "LATTE", "[\"MILK\",\"MILK\",\"SUGAR\"]");
            assertEquals("REGULAR", jdbc.queryForObject("SELECT applied_loyalty_tier FROM orders WHERE id = ?", String.class, original));
            jdbc.update("UPDATE user_accounts SET fulfilled_orders = 6 WHERE id = ?", customer);

            MvcResult reordered = post("/api/orders/" + original + "/reorder", customer, null);

            assertEquals(201, statusOf(reordered));
            long clone = idOf(reordered);
            assertNotEquals(original, clone);
            assertEquals("SILVER", json(reordered, "$.appliedLoyaltyTier"));
            for (long id : new long[]{original, clone}) {
                assertEquals(List.of("MILK", "MILK", "SUGAR"),
                        jdbc.queryForList("SELECT extra_type FROM order_extras WHERE order_id = ? ORDER BY extra_index", String.class, id));
            }
            assertEquals("SILVER", jdbc.queryForObject("SELECT applied_loyalty_tier FROM orders WHERE id = ?", String.class, clone));
            assertEquals("REGULAR", jdbc.queryForObject("SELECT applied_loyalty_tier FROM orders WHERE id = ?", String.class, original),
                    "the original keeps the tier it was placed with");
            assertEquals(customer, count("SELECT customer_id FROM orders WHERE id = ?", clone));
        }
    }

    // ---------------------------------------------------------------- d. loyalty

    @Nested
    @DisplayName("d. loyalty")
    class LoyaltyTests {

        @Test
        @DisplayName("after six fulfilled orders the next order is SILVER and discounted, while earlier orders keep REGULAR")
        void tierRisesForTheNextOrderOnly() throws Exception {
            UserEntity manager = manager("Boss");
            long barista = newStaff(manager, "Bob", "BARISTA");
            long customer = newCustomer("Alice");

            List<Long> earlier = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                earlier.add(completeOrder(customer, barista, "ESPRESSO", "[]"));
            }
            assertEquals(6, fulfilled(customer));

            long next = placeOrder(customer, "ESPRESSO", "[]");

            assertEquals("SILVER", jdbc.queryForObject("SELECT applied_loyalty_tier FROM orders WHERE id = ?", String.class, next));
            assertTrue(jdbc.queryForObject("SELECT price_discount > 0 FROM orders WHERE id = ?", Boolean.class, next));
            for (long id : earlier) {
                assertEquals("REGULAR", jdbc.queryForObject("SELECT applied_loyalty_tier FROM orders WHERE id = ?", String.class, id));
                assertEquals(0, jdbc.queryForObject("SELECT price_discount FROM orders WHERE id = ?", BigDecimal.class, id).signum());
            }
            assertEquals(LoyaltyTier.SILVER, users.findById(customer).orElseThrow().loyaltyTier());
        }
    }

    // ---------------------------------------------------------------- e. failure paths

    @Nested
    @DisplayName("e. failure paths")
    class FailureTests {

        @Test
        @DisplayName("fulfilling an unpaid order is 409 and changes nothing")
        void unpaidFulfil() throws Exception {
            UserEntity manager = manager("Boss");
            long barista = newStaff(manager, "Bob", "BARISTA");
            long customer = newCustomer("Alice");
            long order = placeOrder(customer, "ESPRESSO", "[]");
            awaitStatus(order, "READY");

            MvcResult r = post("/api/orders/" + order + "/fulfil", barista, null);

            assertEquals(409, statusOf(r));
            assertEquals("READY", dbStatus(order));
            assertEquals(0, fulfilled(customer));
        }

        @Test
        @DisplayName("the wrong role is 403 for staff-only and manager-only actions")
        void wrongRole() throws Exception {
            UserEntity manager = manager("Boss");
            long barista = newStaff(manager, "Bob", "BARISTA");
            long customer = newCustomer("Alice");
            long order = placeOrder(customer, "ESPRESSO", "[]");
            awaitStatus(order, "READY");

            assertEquals(403, statusOf(post("/api/orders/" + order + "/fulfil", customer, null)));
            assertEquals(403, statusOf(post("/api/orders/" + order + "/cancel", customer, null)));
            assertEquals(403, statusOf(post("/api/staff", barista, "{\"name\":\"X\",\"role\":\"BARISTA\"}")));
            assertEquals(403, statusOf(post("/api/admin/shop/close", customer, null)));
            assertEquals(403, statusOf(post("/api/chat/sessions", barista, null)));
            assertEquals(403, statusOf(post("/api/chat/barista/ready", customer, null)));
            assertEquals("READY", dbStatus(order));
            assertTrue(coffeeShop.isOpen());
        }

        @Test
        @DisplayName("a missing, non-numeric or unknown X-User-Id is 401 with WWW-Authenticate")
        void unauthenticated() throws Exception {
            long customer = newCustomer("Alice");
            long order = placeOrder(customer, "ESPRESSO", "[]");

            for (MvcResult r : List.of(get("/api/orders/" + order, null),
                    mvc.perform(request(HttpMethod.GET, "/api/orders/" + order).header("X-User-Id", "abc")).andReturn(),
                    get("/api/orders/" + order, 987_654L))) {
                assertEquals(401, statusOf(r));
                assertEquals("X-User-Id", r.getResponse().getHeader("WWW-Authenticate"));
                assertEquals("application/problem+json", r.getResponse().getContentType());
            }
        }

        @Test
        @DisplayName("an unknown order is 404, another customer's order is 403")
        void notFoundAndOthers() throws Exception {
            long alice = newCustomer("Alice");
            long mallory = newCustomer("Mallory");
            long order = placeOrder(alice, "ESPRESSO", "[]");

            assertEquals(404, statusOf(get("/api/orders/987654", alice)));
            assertEquals(403, statusOf(get("/api/orders/" + order, mallory)));
            assertEquals(403, statusOf(post("/api/orders/" + order + "/reorder", mallory, null)));
        }

        @Test
        @DisplayName("a declined payment is 402 with the result; a CASH retry succeeds on the same payments row")
        void declineThenRetry() throws Exception {
            long customer = newCustomer("Alice");
            long order = placeOrder(customer, "ESPRESSO", "[]");
            awaitStatus(order, "READY");

            withGateway(PaymentProvider.PAYPAL, new PayPalAdapter(new PayPalPaymentService(1L)), () -> {
                MvcResult declined = post("/api/orders/" + order + "/pay", customer, "{\"provider\":\"PAYPAL\"}");

                assertEquals(402, statusOf(declined));
                assertEquals("FAILED", json(declined, "$.status"));
                assertEquals(1, count("SELECT count(*) FROM payments WHERE order_id = ? AND status = 'FAILED'", order));
            });
            long paymentId = count("SELECT id FROM payments WHERE order_id = ?", order);

            MvcResult retried = post("/api/orders/" + order + "/pay", customer, "{\"provider\":\"CASH\"}");

            assertEquals(200, statusOf(retried));
            assertEquals(1, count("SELECT count(*) FROM payments WHERE order_id = ?", order));
            assertEquals(paymentId, count("SELECT id FROM payments WHERE order_id = ?", order), "the same row is updated in place");
            assertEquals("PAID", jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId));
            assertEquals("CASH", jdbc.queryForObject("SELECT provider FROM payments WHERE id = ?", String.class, paymentId));
            assertEquals("READY", dbStatus(order));
        }

        @Test
        @DisplayName("a closed shop is 409 for a new order and for a chat /order (the message is kept and the reply explains)")
        void closedShop() throws Exception {
            UserEntity manager = manager("Boss");
            long bob = newStaff(manager, "Bob", "BARISTA");
            long customer = newCustomer("Alice");
            assertEquals(204, statusOf(post("/api/chat/barista/ready", bob, null)));
            long session = idOf(post("/api/chat/sessions", customer, null));

            assertEquals(200, statusOf(post("/api/admin/shop/close", manager.id(), null)));

            assertEquals(409, statusOf(post("/api/orders", customer, "{\"customerId\":" + customer + ",\"type\":\"ESPRESSO\"}")));
            MvcResult chat = post("/api/chat/sessions/" + session + "/messages", customer, "{\"content\":\"/order espresso\"}");
            assertEquals(201, statusOf(chat));
            assertTrue(((String) json(chat, "$.reply.content")).contains("closed"));
            assertFalse(((Map<?, ?>) JsonPath.read(bodyOf(chat), "$")).containsKey("orderId"));
            assertEquals(0, count("SELECT count(*) FROM orders"));
            assertEquals(1, count("SELECT count(*) FROM chat_messages WHERE session_id = ? AND content = '/order espresso'", session));

            assertEquals(200, statusOf(post("/api/admin/shop/open", manager.id(), null)));
            assertTrue(coffeeShop.isOpen());
        }

        @Test
        @DisplayName("a second chat start is 409 carrying the existing session id")
        void duplicateChatStart() throws Exception {
            long customer = newCustomer("Alice");
            long first = idOf(post("/api/chat/sessions", customer, null));

            MvcResult second = post("/api/chat/sessions", customer, null);

            assertEquals(409, statusOf(second));
            assertEquals(first, ((Number) json(second, "$.existingSessionId")).longValue());
            assertEquals(1, count("SELECT count(*) FROM chat_sessions"));
        }

        @Test
        @DisplayName("preparing an order that is already READY is 409 (illegal transition)")
        void illegalTransition() throws Exception {
            UserEntity manager = manager("Boss");
            long barista = newStaff(manager, "Bob", "BARISTA");
            long customer = newCustomer("Alice");
            long order = placeOrder(customer, "ESPRESSO", "[]");
            awaitStatus(order, "READY");

            MvcResult r = post("/api/orders/" + order + "/prepare", barista, null);

            assertEquals(409, statusOf(r));
            assertTrue(((String) json(r, "$.detail")).contains("Illegal order transition"));
            assertEquals("READY", dbStatus(order));
        }

        @Test
        @DisplayName("malformed JSON, an unknown enum value and a non-numeric id are 400 problem details, and nothing is stored")
        void badRequests() throws Exception {
            long customer = newCustomer("Alice");

            for (MvcResult r : List.of(post("/api/orders", customer, "{not json"),
                    post("/api/orders", customer, "{\"customerId\":" + customer + ",\"type\":\"MOCHA\"}"),
                    get("/api/orders/abc", customer))) {
                assertEquals(400, statusOf(r));
                assertEquals("application/problem+json", r.getResponse().getContentType());
            }
            assertEquals(0, count("SELECT count(*) FROM orders"));
        }
    }

    // ---------------------------------------------------------------- f. concurrency

    @Nested
    @DisplayName("f. concurrency over HTTP")
    class ConcurrencyTests {

        @Test
        @DisplayName("two simultaneous pays of one order: exactly one 200 and one 409, one PAID payment row")
        void doublePay() throws Exception {
            long customer = newCustomer("Alice");
            long order = placeOrder(customer, "ESPRESSO", "[]");
            awaitStatus(order, "READY");

            List<Integer> statuses = concurrently(List.<Callable<Integer>>of(
                    () -> statusOf(post("/api/orders/" + order + "/pay", customer, "{\"provider\":\"CASH\"}")),
                    () -> statusOf(post("/api/orders/" + order + "/pay", customer, "{\"provider\":\"CASH\"}"))));

            assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
            assertEquals(1, count("SELECT count(*) FROM payments WHERE order_id = ?", order));
            assertEquals("PAID", jdbc.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, order));
        }

        @Test
        @DisplayName("two customers and two baristas arriving together: two ACTIVE sessions, distinct baristas, one 'joined' message each")
        void twoByTwo() throws Exception {
            UserEntity manager = manager("Boss");
            long b1 = newStaff(manager, "Bea", "BARISTA");
            long b2 = newStaff(manager, "Bob", "BARISTA");
            long c1 = newCustomer("Alice");
            long c2 = newCustomer("Carl");

            List<Integer> statuses = concurrently(List.<Callable<Integer>>of(
                    () -> statusOf(post("/api/chat/barista/ready", b1, null)),
                    () -> statusOf(post("/api/chat/barista/ready", b2, null)),
                    () -> statusOf(post("/api/chat/sessions", c1, null)),
                    () -> statusOf(post("/api/chat/sessions", c2, null))));

            assertEquals(List.of(201, 201, 204, 204), statuses.stream().sorted().toList());
            assertEquals(2, count("SELECT count(*) FROM chat_sessions WHERE status = 'ACTIVE'"));
            assertEquals(2, count("SELECT count(DISTINCT barista_id) FROM chat_sessions WHERE status = 'ACTIVE'"));
            assertEquals(2, count("SELECT count(*) FROM chat_messages WHERE content LIKE '% joined the chat'"));
        }
    }

    // ---------------------------------------------------------------- g. recovery

    @Nested
    @DisplayName("g. restart recovery")
    class RecoveryTests {

        @Test
        @DisplayName("PLACED orders left behind by a 'crash' are re-enqueued by the recovery hook and processed by the pipeline")
        void recoversPlacedOrders() {
            UserEntity alice = customer("Alice");
            Instant now = Instant.now();
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                ids.add(orders.saveAndFlush(new OrderEntity(alice, CoffeeType.ESPRESSO, List.of(), OrderStatus.PLACED,
                        LoyaltyTier.REGULAR, PriceBreakdown.of(new BigDecimal("2.50"), BigDecimal.ZERO, BigDecimal.ZERO), now, now)).id());
            }
            for (long id : ids) {
                assertEquals("PLACED", dbStatus(id), "nothing knows about these rows yet");
            }

            orderRecovery.recover();

            await().atMost(ASYNC).until(() -> ids.stream().allMatch(id -> "READY".equals(dbStatus(id))));
            for (long id : ids) {
                assertEquals(1, count("SELECT count(*) FROM order_status_history WHERE order_id = ? AND to_status = 'READY'", id),
                        "each recovered order is prepared exactly once");
            }
        }
    }
}
