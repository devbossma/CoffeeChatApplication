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
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@DisplayName("OrderEventPublisher")
@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    ApplicationEventPublisher springPublisher;

    @Nested
    @DisplayName("publishStatusChange()")
    class PublishStatusChangeTests {

        @Test
        @DisplayName("publishes an OrderStatusChangedEvent carrying the from/to and ids")
        void publishesEvent() {
            OrderEventPublisher publisher = new OrderEventPublisher(springPublisher);
            OrderEntity order = TestEntities.placedEspresso(11L, TestEntities.customer(3L));
            order.transitionTo(OrderStatus.PREPARING);

            publisher.publishStatusChange(order, OrderStatus.PLACED);

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            verify(springPublisher).publishEvent(captor.capture());

            OrderStatusChangedEvent event = captor.getValue();
            assertEquals(11L, event.orderId());
            assertEquals(3L, event.customerId());
            assertEquals(OrderStatus.PLACED, event.from());
            assertEquals(OrderStatus.PREPARING, event.to());
        }
    }
}
