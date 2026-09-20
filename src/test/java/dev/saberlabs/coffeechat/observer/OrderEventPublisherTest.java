package dev.saberlabs.coffeechat.observer;

import dev.saberlabs.coffeechat.entity.OrderEntity;
import dev.saberlabs.coffeechat.model.OrderStatus;
import dev.saberlabs.coffeechat.support.TestEntities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("OrderEventPublisher")
@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    ApplicationEventPublisher springPublisher;

    /** The publisher refuses to run outside a transaction; simulate one being active. */
    @BeforeEach
    void activeTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Nested
    @DisplayName("publishStatusChange()")
    class PublishStatusChangeTests {

        @Test
        @DisplayName("publishes an OrderStatusChangedEvent carrying the from/to and ids")
        void publishesEvent() {
            OrderEventPublisher publisher = new OrderEventPublisher(springPublisher);
            OrderEntity order = TestEntities.placedEspresso(11L, TestEntities.customer(3L));
            order.transitionTo(OrderStatus.PREPARING);

            publisher.publishStatusChange(order, OrderStatus.PLACED, null);

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            verify(springPublisher).publishEvent(captor.capture());

            OrderStatusChangedEvent event = captor.getValue();
            assertEquals(11L, event.orderId());
            assertEquals(3L, event.customerId());
            assertEquals(OrderStatus.PLACED, event.from());
            assertEquals(OrderStatus.PREPARING, event.to());
            assertNull(event.actorUserId());
        }

        @Test
        @DisplayName("carries the actor when one is supplied (the Step 4 seam)")
        void carriesActor() {
            OrderEventPublisher publisher = new OrderEventPublisher(springPublisher);
            OrderEntity order = TestEntities.placedEspresso(11L, TestEntities.customer(3L));

            publisher.publishStatusChange(order, OrderStatus.PLACED, 77L);

            ArgumentCaptor<OrderStatusChangedEvent> captor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            verify(springPublisher).publishEvent(captor.capture());
            assertEquals(77L, captor.getValue().actorUserId());
        }

        @Test
        @DisplayName("refuses to publish outside a transaction, so an AFTER_COMMIT listener can never silently drop the event")
        void refusesWithoutTransaction() {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            OrderEventPublisher publisher = new OrderEventPublisher(springPublisher);
            OrderEntity order = TestEntities.placedEspresso(11L, TestEntities.customer(3L));

            assertThrows(IllegalStateException.class,
                    () -> publisher.publishStatusChange(order, OrderStatus.PLACED, null));

            verify(springPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
        }
    }
}
