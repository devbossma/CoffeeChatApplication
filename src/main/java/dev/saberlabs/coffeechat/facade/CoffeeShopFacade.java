package dev.saberlabs.coffeechat.facade;

import dev.saberlabs.coffeechat.adapter.PaymentGatewayResolver;
import dev.saberlabs.coffeechat.adapter.PaymentProvider;
import dev.saberlabs.coffeechat.adapter.PaymentResult;
import dev.saberlabs.coffeechat.command.CancelOrderCommand;
import dev.saberlabs.coffeechat.command.FulfillOrderCommand;
import dev.saberlabs.coffeechat.command.OrderInvoker;
import dev.saberlabs.coffeechat.command.PayOrderCommand;
import dev.saberlabs.coffeechat.command.PlaceOrderCommand;
import dev.saberlabs.coffeechat.command.PrepareOrderCommand;
import dev.saberlabs.coffeechat.decorator.CoffeeDecorators;
import dev.saberlabs.coffeechat.factory.CoffeeFactory;
import dev.saberlabs.coffeechat.model.Coffee;
import dev.saberlabs.coffeechat.model.Customer;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.prototype.OrderPrototype;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import dev.saberlabs.coffeechat.strategy.PricingStrategy;
import dev.saberlabs.coffeechat.strategy.PricingStrategyResolver;
import dev.saberlabs.coffeechat.template.CoffeePreparationResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Pattern 8: FACADE.
 *
 * <p>The single door into the order lifecycle. Controllers (and, later, {@code ChatService} and
 * the async Barista) call <em>only</em> this class &mdash; never {@code OrderService},
 * {@code OrderInvoker} or a {@code Command} directly. It is the only class that builds
 * {@code Command} objects and hands them to {@link OrderInvoker} ({@code CLAUDE.md}).
 *
 * <p>{@link #placeOrder} coordinates the other patterns: Singleton (is the shop open? on menu?),
 * Factory Method (base coffee), Decorator (extras), Strategy (tier price), Command (the
 * PlaceOrderCommand), Observer (the event the command publishes).
 */
@Service
public class CoffeeShopFacade {

    private final CoffeeShop coffeeShop;
    private final CoffeeFactory coffeeFactory;
    private final PricingStrategyResolver pricing;
    private final CoffeePreparationResolver preparations;
    private final PaymentGatewayResolver gateways;
    private final OrderService orders;
    private final CustomerService customers;
    private final OrderEventPublisher events;
    private final OrderInvoker invoker;
    private final ObjectProvider<OrderPrototype> orderPrototypeProvider;

    public CoffeeShopFacade(CoffeeShop coffeeShop,
                            CoffeeFactory coffeeFactory,
                            PricingStrategyResolver pricing,
                            CoffeePreparationResolver preparations,
                            PaymentGatewayResolver gateways,
                            OrderService orders,
                            CustomerService customers,
                            OrderEventPublisher events,
                            OrderInvoker invoker,
                            ObjectProvider<OrderPrototype> orderPrototypeProvider) {
        this.coffeeShop = coffeeShop;
        this.coffeeFactory = coffeeFactory;
        this.pricing = pricing;
        this.preparations = preparations;
        this.gateways = gateways;
        this.orders = orders;
        this.customers = customers;
        this.events = events;
        this.invoker = invoker;
        this.orderPrototypeProvider = orderPrototypeProvider;
    }

    /**
     * Places a new order: validates the shop is open and the coffee is on the menu, builds the
     * (decorated, tier-priced) order, and runs it through {@link PlaceOrderCommand}.
     *
     * @throws ShopClosedException       if the shop is not accepting orders
     * @throws CoffeeNotOnMenuException  if the requested type is off the menu
     * @throws CustomerNotFoundException if {@code request.customerId()} is unknown
     */
    public Order placeOrder(PlaceOrderRequest request) {
        if (!coffeeShop.isOpen()) {
            throw new ShopClosedException();
        }
        if (!coffeeShop.isOnMenu(request.type())) {
            throw new CoffeeNotOnMenuException(request.type());
        }
        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));

        Coffee base = coffeeFactory.create(request.type());
        Coffee decorated = CoffeeDecorators.decorate(base, request.extras());

        LoyaltyTier tier = customer.loyaltyTier();
        PricingStrategy strategy = pricing.forTier(tier);

        BigDecimal fullCost = decorated.cost();
        BigDecimal extrasTotal = fullCost.subtract(base.cost());
        BigDecimal discount = strategy.discountAmount(fullCost);
        BigDecimal total = strategy.priceFor(fullCost);
        PriceBreakdown price = new PriceBreakdown(base.cost(), extrasTotal, discount, total);

        Order order = new Order(customer, decorated, request.type(), request.extras(), price, tier);
        invoker.executeCommand(new PlaceOrderCommand(order, orders, events));
        return order;
    }

    /** @throws OrderNotFoundException if no order has that id. */
    public Order getOrder(Long orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Prepare an order: Template Method recipe, then {@code PLACED -> READY}. */
    public void prepareOrder(Long orderId) {
        Order order = getOrder(orderId);
        invoker.executeCommand(new PrepareOrderCommand(order, orders, events, preparations));
    }

    /** Collect payment for an order through {@code provider}'s Adapter. */
    public PaymentResult payOrder(Long orderId, PaymentProvider provider) {
        Order order = getOrder(orderId);
        PayOrderCommand command = new PayOrderCommand(order, provider, gateways);
        invoker.executeCommand(command);
        return command.result();
    }

    /** Fulfil an order: {@code READY -> FULFILLED} and bump the customer's fulfilled count. */
    public void fulfillOrder(Long orderId) {
        Order order = getOrder(orderId);
        invoker.executeCommand(new FulfillOrderCommand(order, orders, events, customers));
    }

    /** Cancel an in-progress order. */
    public void cancelOrder(Long orderId) {
        Order order = getOrder(orderId);
        invoker.executeCommand(new CancelOrderCommand(order, orders, events));
    }

    /**
     * Run an order through the rest of its lifecycle synchronously: prepare, pay, fulfil. This
     * is {@code MyDesignPattern}'s {@code processOrder}; Part 02 replaces it with the async
     * Barista pipeline.
     */
    public Order processOrder(Long orderId, PaymentProvider provider) {
        prepareOrder(orderId);
        payOrder(orderId, provider);
        fulfillOrder(orderId);
        return getOrder(orderId);
    }

    /**
     * Re-order an existing order (Pattern 9: PROTOTYPE). Fetches a fresh prototype-scoped
     * {@link OrderPrototype}, seeds it from the original's structure (base coffee + extras +
     * customer, never id/status/timestamps), and re-places it &mdash; ending in
     * {@link #placeOrder(PlaceOrderRequest)}, not a separate path.
     *
     * @throws OrderNotFoundException if {@code orderId} is unknown
     */
    public Order reorder(Long orderId) {
        Order original = getOrder(orderId);
        OrderPrototype prototype = orderPrototypeProvider.getObject();
        prototype.copyOf(original);
        return placeOrder(prototype.toPlaceOrderRequest());
    }

    /** Undo the most recent lifecycle action, if any. */
    public void undoLastAction() {
        invoker.undoLast();
    }
}
