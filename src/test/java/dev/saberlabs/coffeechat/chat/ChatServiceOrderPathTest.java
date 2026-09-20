package dev.saberlabs.coffeechat.chat;

import dev.saberlabs.coffeechat.chat.ChatService.SendResult;
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.facade.Actor;
import dev.saberlabs.coffeechat.facade.CoffeeShopFacade;
import dev.saberlabs.coffeechat.facade.PlaceOrderRequest;
import dev.saberlabs.coffeechat.model.CoffeeType;
import dev.saberlabs.coffeechat.model.ExtraType;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.MessageType;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.model.Role;
import dev.saberlabs.coffeechat.model.SessionStatus;
import dev.saberlabs.coffeechat.repository.UserRepository;
import dev.saberlabs.coffeechat.service.StaffAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The T3 (confirmation) retry rules of the /order path, with the store faked so its failures can be injected. */
@DisplayName("ChatService /order path: confirmation retries (faked store)")
class ChatServiceOrderPathTest {

    private static final long SESSION = 5L;
    private static final long ORDER = 77L;

    private ChatSessionStore store;
    private CoffeeShopFacade facade;
    private ChatService chat;
    private Actor customerActor;

    @BeforeEach
    void setUp() {
        store = mock(ChatSessionStore.class);
        facade = mock(CoffeeShopFacade.class);
        UserRepository users = mock(UserRepository.class);
        UserEntity customer = mock(UserEntity.class);
        when(customer.id()).thenReturn(11L);
        when(customer.name()).thenReturn("Alice");
        when(customer.role()).thenReturn(Role.CUSTOMER);
        when(users.findById(11L)).thenReturn(Optional.of(customer));
        customerActor = Actor.user(11L);

        when(store.find(SESSION)).thenReturn(Optional.of(new SessionView(SESSION, 11L, 12L, SessionStatus.ACTIVE, Instant.now())));
        when(store.addMessage(eq(SESSION), eq(MessageType.CHAT_MESSAGE), anyLong(), any(), any(), any()))
                .thenReturn(new MessageView(1, SESSION, MessageType.CHAT_MESSAGE, 11L, "Alice", "/order latte", Instant.now(), null));
        when(facade.placeOrder(any(PlaceOrderRequest.class))).thenReturn(order());

        chat = new ChatService(mock(ChatMatchmaker.class), store, new OrderCommandParser(), facade, mock(StaffAccess.class), users);
    }

    private static Order order() {
        Instant now = Instant.now();
        return new Order(ORDER, 11L, CoffeeType.LATTE, List.<ExtraType>of(), "Latte",
                PriceBreakdown.of(new BigDecimal("3.50"), BigDecimal.ZERO, BigDecimal.ZERO),
                LoyaltyTier.REGULAR, OrderStatus.PLACED, now, now);
    }

    private MessageView confirmation() {
        return new MessageView(2, SESSION, MessageType.SYSTEM_MESSAGE, null, "System", "Order #77 placed", Instant.now(), ORDER);
    }

    @Nested
    @DisplayName("T3 confirmation")
    class ConfirmationTests {

        @Test
        @DisplayName("a transient failure is retried; the order is placed once and the customer message stored once")
        void retriedThenStored() {
            when(store.addMessage(eq(SESSION), eq(MessageType.SYSTEM_MESSAGE), any(), any(), any(), eq(ORDER)))
                    .thenThrow(new IllegalStateException("db blip"))
                    .thenThrow(new IllegalStateException("db blip"))
                    .thenReturn(confirmation());

            SendResult result = chat.sendMessage(customerActor, SESSION, "/order latte");

            assertEquals(Long.valueOf(ORDER), result.orderId());
            assertNotNull(result.reply());
            verify(facade, times(1)).placeOrder(any());
            verify(store, times(1)).addMessage(eq(SESSION), eq(MessageType.CHAT_MESSAGE), any(), any(), any(), any());
            verify(store, times(3)).addMessage(eq(SESSION), eq(MessageType.SYSTEM_MESSAGE), any(), any(), any(), eq(ORDER));
        }

        @Test
        @DisplayName("when every attempt fails the order still stands: the result carries its id, with no reply, and the order is not re-placed")
        void allAttemptsFail() {
            when(store.addMessage(eq(SESSION), eq(MessageType.SYSTEM_MESSAGE), any(), any(), any(), eq(ORDER)))
                    .thenThrow(new IllegalStateException("db down"));

            SendResult result = chat.sendMessage(customerActor, SESSION, "/order latte");

            assertEquals(Long.valueOf(ORDER), result.orderId());
            assertNull(result.reply());
            assertEquals("/order latte", result.message().content());
            verify(facade, times(1)).placeOrder(any());
            verify(store, times(ChatService.CONFIRMATION_ATTEMPTS)).addMessage(eq(SESSION), eq(MessageType.SYSTEM_MESSAGE), any(), any(), any(), eq(ORDER));
        }

        @Test
        @DisplayName("a failure while storing the customer's own message places no order")
        void t1FailureStopsEverything() {
            when(store.addMessage(eq(SESSION), eq(MessageType.CHAT_MESSAGE), anyLong(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("db down"));

            assertThrows(IllegalStateException.class, () -> chat.sendMessage(customerActor, SESSION, "/order latte"));

            verify(facade, never()).placeOrder(any());
        }
    }

    @Test
    @DisplayName("constructor rejects null collaborators")
    void constructorNulls() {
        StaffAccess access = mock(StaffAccess.class);
        UserRepository users = mock(UserRepository.class);
        ChatMatchmaker matchmaker = mock(ChatMatchmaker.class);
        OrderCommandParser parser = new OrderCommandParser();
        assertThrows(NullPointerException.class, () -> new ChatService(null, store, parser, facade, access, users));
        assertThrows(NullPointerException.class, () -> new ChatService(matchmaker, null, parser, facade, access, users));
        assertThrows(NullPointerException.class, () -> new ChatService(matchmaker, store, null, facade, access, users));
        assertThrows(NullPointerException.class, () -> new ChatService(matchmaker, store, parser, null, access, users));
        assertThrows(NullPointerException.class, () -> new ChatService(matchmaker, store, parser, facade, null, users));
        assertThrows(NullPointerException.class, () -> new ChatService(matchmaker, store, parser, facade, access, null));
    }
}
