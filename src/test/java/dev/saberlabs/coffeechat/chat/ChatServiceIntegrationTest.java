package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.chat.ChatService.SendResult;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.RoleNotAllowedException;
import dev.saberlabs.coffeechat.facade.UnknownActorException;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.MessageType;
import dev.saberlabs.coffeechat.model.SessionStatus;
import dev.saberlabs.coffeechat.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ChatService (real database, real facade)")
class ChatServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ChatService chat;
    @Autowired private ChatSessionStore store;

    private UserEntity alice;
    private UserEntity bob;
    private SessionView session;

    @BeforeEach
    void seed() {
        alice = customer("Alice");
        bob = barista("Bob");
        chat.baristaReady(as(bob));
        session = chat.startChat(as(alice));
    }

    private static Actor as(UserEntity user) {
        return Actor.user(user.id());
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private List<String> texts() {
        return store.history(session.id()).stream().map(MessageView::content).toList();
    }

    @Nested
    @DisplayName("startChat()")
    class StartChatTests {

        @Test
        @DisplayName("a customer with a ready barista gets an ACTIVE session at once")
        void matched() {
            assertEquals(SessionStatus.ACTIVE, session.status());
            assertEquals(bob.id(), session.baristaId());
        }

        @Test
        @DisplayName("with no barista ready the session is WAITING")
        void waiting() {
            UserEntity carl = customer("Carl");
            assertEquals(SessionStatus.WAITING, chat.startChat(as(carl)).status());
        }

        @Test
        @DisplayName("a second start is refused with the existing session's id")
        void alreadyOpen() {
            ChatSessionAlreadyOpenException e = assertThrows(ChatSessionAlreadyOpenException.class, () -> chat.startChat(as(alice)));
            assertEquals(Long.valueOf(session.id()), e.existingSessionId());
        }

        @Test
        @DisplayName("only a CUSTOMER may start a chat")
        void onlyCustomers() {
            assertThrows(RoleNotAllowedException.class, () -> chat.startChat(as(bob)));
            assertThrows(RoleNotAllowedException.class, () -> chat.startChat(as(manager("Mona"))));
        }

        @Test
        @DisplayName("the system actor and unknown users are unauthenticated, not merely forbidden")
        void unauthenticated() {
            assertThrows(UnknownActorException.class, () -> chat.startChat(Actor.SYSTEM));
            assertThrows(UnknownActorException.class, () -> chat.startChat(Actor.user(987654L)));
        }

        @Test
        @DisplayName("rejects a null actor")
        void nullActor() {
            assertThrows(NullPointerException.class, () -> chat.startChat(null));
        }
    }

    @Nested
    @DisplayName("baristaReady() / baristaOffline()")
    class BaristaTests {

        @Test
        @DisplayName("a ready barista takes the longest-waiting customer")
        void takesWaiting() {
            UserEntity carl = customer("Carl");
            SessionView waiting = chat.startChat(as(carl));
            UserEntity bea = barista("Bea");

            chat.baristaReady(as(bea));

            assertEquals(SessionStatus.ACTIVE, store.find(waiting.id()).orElseThrow().status());
        }

        @Test
        @DisplayName("only a BARISTA may register as ready or go offline")
        void onlyBaristas() {
            assertThrows(RoleNotAllowedException.class, () -> chat.baristaReady(as(alice)));
            assertThrows(RoleNotAllowedException.class, () -> chat.baristaOffline(as(manager("Mona"))));
            assertThrows(UnknownActorException.class, () -> chat.baristaReady(Actor.SYSTEM));
            assertThrows(UnknownActorException.class, () -> chat.baristaReady(Actor.user(987654L)));
        }

        @Test
        @DisplayName("an offline barista is not matched with the next customer")
        void offline() {
            UserEntity bea = barista("Bea");
            chat.baristaReady(as(bea));
            chat.baristaOffline(as(bea));

            SessionView waiting = chat.startChat(as(customer("Carl")));

            assertEquals(SessionStatus.WAITING, waiting.status());
        }
    }

    @Nested
    @DisplayName("sendMessage()")
    class SendMessageTests {

        @Test
        @DisplayName("stores a plain message from each participant, in order, with no order placed")
        void plainMessages() {
            SendResult first = chat.sendMessage(as(alice), session.id(), "  hello there  ");
            SendResult second = chat.sendMessage(as(bob), session.id(), "hi Alice");

            assertEquals("hello there", first.message().content());
            assertEquals(MessageType.CHAT_MESSAGE, second.message().type());
            assertNull(first.reply());
            assertNull(first.orderId());
            assertEquals(List.of("Bob joined the chat", "hello there", "hi Alice"), texts());
            assertEquals(0, count("SELECT count(*) FROM orders"));
        }

        @Test
        @DisplayName("a WAITING session cannot be posted to")
        void waitingSession() {
            UserEntity carl = customer("Carl");
            SessionView waiting = chat.startChat(as(carl));

            assertThrows(SessionNotActiveException.class, () -> chat.sendMessage(as(carl), waiting.id(), "anyone?"));
        }

        @Test
        @DisplayName("an ended session cannot be posted to")
        void endedSession() {
            chat.endSession(as(alice), session.id());

            assertThrows(SessionNotActiveException.class, () -> chat.sendMessage(as(alice), session.id(), "still there?"));
        }

        @Test
        @DisplayName("only the session's own customer and barista may post")
        void participantsOnly() {
            UserEntity carl = customer("Carl");
            UserEntity bea = barista("Bea");

            assertThrows(NotChatParticipantException.class, () -> chat.sendMessage(as(carl), session.id(), "hi"));
            assertThrows(NotChatParticipantException.class, () -> chat.sendMessage(as(bea), session.id(), "hi"));
        }

        @Test
        @DisplayName("a manager may not post, and the system actor is unauthenticated")
        void managerAndSystem() {
            assertThrows(RoleNotAllowedException.class, () -> chat.sendMessage(as(manager("Mona")), session.id(), "hi"));
            assertThrows(UnknownActorException.class, () -> chat.sendMessage(Actor.SYSTEM, session.id(), "hi"));
        }

        @Test
        @DisplayName("an unknown session is 404")
        void unknownSession() {
            assertThrows(ChatSessionNotFoundException.class, () -> chat.sendMessage(as(alice), 424242L, "hi"));
        }

        @Test
        @DisplayName("null, empty, ASCII-blank and Unicode-space-only content is rejected, and nothing is stored")
        void blankRejected() {
            for (String blank : new String[]{null, "", "   ", "\t\n", " ", "    　"}) {
                assertThrows(InvalidChatMessageException.class, () -> chat.sendMessage(as(alice), session.id(), blank));
            }
            assertEquals(List.of("Bob joined the chat"), texts());
        }

        @Test
        @DisplayName("2000 characters is accepted, 2001 is rejected")
        void lengthLimit() {
            assertEquals(2000, chat.sendMessage(as(alice), session.id(), "x".repeat(2000)).message().content().length());
            assertThrows(InvalidChatMessageException.class, () -> chat.sendMessage(as(alice), session.id(), "x".repeat(2001)));
        }

        @Test
        @DisplayName("surrounding non-breaking spaces are trimmed")
        void unicodeTrim() {
            assertEquals("hi", chat.sendMessage(as(alice), session.id(), " hi ").message().content());
        }
    }

    @Nested
    @DisplayName("the /order path")
    class OrderPathTests {

        @Test
        @DisplayName("places exactly one order through the facade, and replies with a confirmation linked to it")
        void placesOrder() {
            SendResult result = chat.sendMessage(as(alice), session.id(), "/order latte milk sugar");

            assertNotNull(result.orderId());
            assertEquals(1, count("SELECT count(*) FROM orders"));
            assertEquals(alice.id(), jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = ?", Long.class, result.orderId()));
            assertEquals("PLACED", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, result.orderId()));
            assertEquals(result.orderId(), result.reply().orderId());
            assertEquals(MessageType.SYSTEM_MESSAGE, result.reply().type());
            assertTrue(result.reply().content().startsWith("Order #" + result.orderId() + " placed"));
            assertEquals(1, count("SELECT count(*) FROM chat_messages WHERE type = 'CHAT_MESSAGE'"));
            assertEquals(3, store.history(session.id()).size());
        }

        @Test
        @DisplayName("keeps the customer's own message even when the order is refused")
        void closedShop() {
            coffeeShop.close();

            SendResult result = chat.sendMessage(as(alice), session.id(), "/order espresso");

            assertNull(result.orderId());
            assertEquals("/order espresso", result.message().content());
            assertTrue(result.reply().content().contains("closed"));
            assertEquals(0, count("SELECT count(*) FROM orders"));
        }

        @Test
        @DisplayName("a coffee that is off the menu is explained, not ordered")
        void offMenu() {
            coffeeShop.stopServing(CoffeeType.LATTE);

            SendResult result = chat.sendMessage(as(alice), session.id(), "/order latte");

            assertNull(result.orderId());
            assertTrue(result.reply().content().contains("not on the menu"));
            assertEquals(0, count("SELECT count(*) FROM orders"));
        }

        @Test
        @DisplayName("a bare /order, an unknown coffee and unknown extras each get a helpful reply and no order")
        void malformed() {
            assertTrue(chat.sendMessage(as(alice), session.id(), "/order").reply().content().startsWith("Which coffee?"));
            assertTrue(chat.sendMessage(as(alice), session.id(), "/order mocha").reply().content().contains("'mocha'"));
            assertTrue(chat.sendMessage(as(alice), session.id(), "/order latte caramel vanilla").reply().content().contains("caramel, vanilla"));
            assertEquals(0, count("SELECT count(*) FROM orders"));
        }

        @Test
        @DisplayName("the PRD false-positive sentence and look-alike commands are plain messages")
        void notOrders() {
            for (String text : new String[]{"order latte from this place was amazing", "/orders latte", "please /order latte"}) {
                SendResult result = chat.sendMessage(as(alice), session.id(), text);
                assertNull(result.reply(), text);
                assertNull(result.orderId(), text);
            }
            assertEquals(0, count("SELECT count(*) FROM orders"));
        }

        @Test
        @DisplayName("a leading non-breaking space does not hide the command")
        void leadingNbsp() {
            SendResult result = chat.sendMessage(as(alice), session.id(), " /order latte");

            assertNotNull(result.orderId());
        }

        @Test
        @DisplayName("a barista typing /order is just talking: no order is placed")
        void baristaCannotOrder() {
            SendResult result = chat.sendMessage(as(bob), session.id(), "/order latte");

            assertNull(result.orderId());
            assertNull(result.reply());
            assertEquals(0, count("SELECT count(*) FROM orders"));
        }

        @Test
        @DisplayName("known limitation: no idempotency key, so a retried /order message places a second order")
        void retriedOrderPlacesAnother() {
            SendResult first = chat.sendMessage(as(alice), session.id(), "/order latte");
            SendResult second = chat.sendMessage(as(alice), session.id(), "/order latte");

            assertFalse(first.orderId().equals(second.orderId()));
            assertEquals(2, count("SELECT count(*) FROM orders"));
        }
    }

    @Nested
    @DisplayName("the store's own check when a message is inserted")
    class InsertTimeCheckTests {

        @Test
        @DisplayName("a session that ended between the caller's check and the insert receives nothing (deterministic: ended directly in the database)")
        void endedBetweenCheckAndInsert() {
            jdbc.update("UPDATE chat_sessions SET status = 'INACTIVE' WHERE id = ?", session.id());

            assertThrows(SessionNotActiveException.class,
                    () -> store.addMessage(session.id(), MessageType.CHAT_MESSAGE, alice.id(), "Alice", "too late", null));
            assertEquals(List.of("Bob joined the chat"), texts());
        }

        @Test
        @DisplayName("a barista who was un-assigned between the check and the insert may not post")
        void unassignedBarista() {
            UserEntity bea = barista("Bea");
            jdbc.update("UPDATE chat_sessions SET barista_id = ? WHERE id = ?", bea.id(), session.id());

            assertThrows(NotChatParticipantException.class,
                    () -> store.addMessage(session.id(), MessageType.CHAT_MESSAGE, bob.id(), "Bob", "still me", null));
            assertEquals(1, store.history(session.id()).size());
            assertEquals(MessageType.CHAT_MESSAGE,
                    store.addMessage(session.id(), MessageType.CHAT_MESSAGE, bea.id(), "Bea", "hello", null).type());
        }

        @Test
        @DisplayName("a stranger is refused, and a CHAT_MESSAGE needs a sender")
        void strangerAndNoSender() {
            UserEntity carl = customer("Carl");

            assertThrows(NotChatParticipantException.class,
                    () -> store.addMessage(session.id(), MessageType.CHAT_MESSAGE, carl.id(), "Carl", "hi", null));
            assertThrows(IllegalArgumentException.class,
                    () -> store.addMessage(session.id(), MessageType.CHAT_MESSAGE, null, "Nobody", "hi", null));
        }

        @Test
        @DisplayName("the system may still write to an ended session (the /order confirmation must not be lost)")
        void systemMessageAlwaysAllowed() {
            chat.endSession(as(alice), session.id());

            assertEquals(MessageType.SYSTEM_MESSAGE,
                    store.addMessage(session.id(), MessageType.SYSTEM_MESSAGE, null, "System", "note", null).type());
        }

        @Test
        @DisplayName("a message racing the end of its session either lands before the end or is refused; never after")
        void raceWithEnd() throws Exception {
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var go = new java.util.concurrent.CountDownLatch(1);
                var send = pool.submit(() -> {
                    go.await();
                    try {
                        store.addMessage(session.id(), MessageType.CHAT_MESSAGE, alice.id(), "Alice", "racing", null);
                        return true;
                    } catch (SessionNotActiveException e) {
                        return false;
                    }
                });
                var end = pool.submit(() -> {
                    go.await();
                    return chat.endSession(as(alice), session.id());
                });
                go.countDown();
                boolean landed = send.get(30, java.util.concurrent.TimeUnit.SECONDS);
                end.get(30, java.util.concurrent.TimeUnit.SECONDS);

                List<MessageView> history = store.history(session.id());
                long endedAt = history.stream().filter(m -> m.content().equals("The chat has ended")).findFirst().orElseThrow().id();
                boolean racingBeforeEnd = history.stream().anyMatch(m -> m.content().equals("racing") && m.id() < endedAt);
                assertEquals(landed, racingBeforeEnd);
                assertFalse(history.stream().anyMatch(m -> m.content().equals("racing") && m.id() > endedAt));
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("transaction guard")
    class TransactionGuardTests {

        @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

        @Test
        @DisplayName("every write operation refuses to run inside a caller's transaction, and changes nothing")
        void refusesInsideTransaction() {
            var template = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            UserEntity carl = customer("Carl");

            assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(s -> chat.startChat(as(carl))));
            assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(s -> chat.baristaReady(as(bob))));
            assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(s -> chat.baristaOffline(as(bob))));
            assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(s -> chat.endSession(as(alice), session.id())));
            assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(s -> chat.sendMessage(as(alice), session.id(), "/order latte")));

            assertEquals(1, count("SELECT count(*) FROM chat_sessions"));
            assertEquals(SessionStatus.ACTIVE, store.find(session.id()).orElseThrow().status());
            assertEquals(0, count("SELECT count(*) FROM orders"));
        }
    }

    @Nested
    @DisplayName("history() and endSession()")
    class HistoryAndEndTests {

        @Test
        @DisplayName("participants and any manager may read the history, oldest first")
        void readers() {
            chat.sendMessage(as(alice), session.id(), "one");
            chat.sendMessage(as(bob), session.id(), "two");

            List<String> expected = List.of("Bob joined the chat", "one", "two");
            assertEquals(expected, chat.history(as(alice), session.id()).stream().map(MessageView::content).toList());
            assertEquals(expected, chat.history(as(bob), session.id()).stream().map(MessageView::content).toList());
            assertEquals(expected, chat.history(as(manager("Mona")), session.id()).stream().map(MessageView::content).toList());
        }

        @Test
        @DisplayName("an outsider may not read it, and an unknown session is 404")
        void outsiders() {
            assertThrows(NotChatParticipantException.class, () -> chat.history(as(customer("Carl")), session.id()));
            assertThrows(NotChatParticipantException.class, () -> chat.history(as(barista("Bea")), session.id()));
            assertThrows(ChatSessionNotFoundException.class, () -> chat.history(as(alice), 424242L));
        }

        @Test
        @DisplayName("the customer, the barista or a manager may end the session; a second end reports false")
        void enders() {
            assertTrue(chat.endSession(as(bob), session.id()));
            assertFalse(chat.endSession(as(alice), session.id()));
            assertFalse(chat.endSession(as(manager("Mona")), session.id()));
            assertEquals(SessionStatus.INACTIVE, store.find(session.id()).orElseThrow().status());
        }

        @Test
        @DisplayName("an outsider may not end it, and an unknown session is 404")
        void outsiderCannotEnd() {
            assertThrows(NotChatParticipantException.class, () -> chat.endSession(as(customer("Carl")), session.id()));
            assertThrows(ChatSessionNotFoundException.class, () -> chat.endSession(as(alice), 424242L));
            assertEquals(SessionStatus.ACTIVE, store.find(session.id()).orElseThrow().status());
        }

        @Test
        @DisplayName("ending frees the barista for the next waiting customer")
        void freesBarista() {
            SessionView waiting = chat.startChat(as(customer("Carl")));

            chat.endSession(as(alice), session.id());

            SessionView rematched = store.find(waiting.id()).orElseThrow();
            assertEquals(SessionStatus.ACTIVE, rematched.status());
            assertEquals(bob.id(), rematched.baristaId());
        }
    }
}
