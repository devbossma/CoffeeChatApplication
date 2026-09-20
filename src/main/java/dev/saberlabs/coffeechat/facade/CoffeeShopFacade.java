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
import dev.saberlabs.coffeechat.entity.UserEntity;
import dev.saberlabs.coffeechat.factory.CoffeeFactory;
import dev.saberlabs.coffeechat.model.Coffee;
import dev.saberlabs.coffeechat.model.LoyaltyTier;
import dev.saberlabs.coffeechat.model.Order;
import dev.saberlabs.coffeechat.model.PriceBreakdown;
import dev.saberlabs.coffeechat.observer.OrderEventPublisher;
import dev.saberlabs.coffeechat.prototype.OrderPrototype;
import dev.saberlabs.coffeechat.repository.UserRepository;
import dev.saberlabs.coffeechat.service.CustomerService;
import dev.saberlabs.coffeechat.service.OrderService;
import dev.saberlabs.coffeechat.service.PaymentService;
import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import dev.saberlabs.coffeechat.strategy.PricingStrategy;
import dev.saberlabs.coffeechat.strategy.PricingStrategyResolver;
import dev.saberlabs.coffeechat.template.CoffeePreparationResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Pattern 8: FACADE &mdash; the only door into the order lifecycle ({@code CLAUDE.md}). Every
 * lifecycle transition, from every entry point (REST, chat, the async barista), goes through here;
 * this is the only class that builds {@code Command}s and calls {@code OrderInvoker}.
 *
 * <p>The facade methods are deliberately <em>not</em> transactional: each command runs in exactly
 * one transaction opened by {@link OrderInvoker}, which is also what lets the barista thread (no
 * ambient transaction) call in safely.
 */
@Service
public class CoffeeShopFacade {

    private final CoffeeShop coffeeShop;
    private final CoffeeFactory coffeeFactory;
    private final PricingStrategyResolver pricing;
    private final CoffeePreparationResolver preparations;
    private final PaymentGatewayResolver gateways;
    private final OrderService orders;
    private final PaymentService payments;
    private final CustomerService customers;
    private final UserRepository users;
    private final OrderEventPublisher events;
    private final OrderInvoker invoker;
    private final ObjectProvider<OrderPrototype> orderPrototypeProvider;

    public CoffeeShopFacade(CoffeeShop coffeeShop,
                            CoffeeFactory coffeeFactory,
                            PricingStrategyResolver pricing,
                            CoffeePreparationResolver preparations,
                            PaymentGatewayResolver gateways,
                            OrderService orders,
                            PaymentService payments,
                            CustomerService customers,
                            UserRepository users,
                            OrderEventPublisher events,
                            OrderInvoker invoker,
                            ObjectProvider<OrderPrototype> orderPrototypeProvider) {
        this.coffeeShop = coffeeShop;
        this.coffeeFactory = coffeeFactory;
        this.pricing = pricing;
        this.preparations = preparations;
        this.gateways = gateways;
        this.orders = orders;
        this.payments = payments;
        this.customers = customers;
        this.users = users;
        this.events = events;
        this.invoker = invoker;
        this.orderPrototypeProvider = orderPrototypeProvider;
    }

    /**
     * Places a new order: validates the shop is open and the coffee is on the menu, derives the
     * customer's <em>current</em> loyalty tier from {@code fulfilled_orders} (frozen into the order
     * from here on), prices it, and runs {@link PlaceOrderCommand}.
     *
     * @throws ShopClosedException       if the shop is not accepting orders
     * @throws CoffeeNotOnMenuException  if the requested type is off the menu
     * @throws CustomerNotFoundException if {@code request.customerId()} is not a CUSTOMER user
     */
    public Order placeOrder(PlaceOrderRequest request) {
        if (!coffeeShop.isOpen()) {
            throw new ShopClosedException();
        }
        if (!coffeeShop.isOnMenu(request.type())) {
            throw new CoffeeNotOnMenuException(request.type());
        }
        UserEntity customer = customers.findById(request.customerId())
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

        PlaceOrderCommand command = new PlaceOrderCommand(
                request.customerId(), request.type(), request.extras(), tier, price, customers, orders, events);
        invoker.executeCommand(command);
        return getOrder(command.orderId());
    }

    /** @throws OrderNotFoundException if no order has that id. */
    public Order getOrder(Long orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Prepare an order: Template Method recipe, then {@code PLACED -> READY}. */
    public void prepareOrder(Long orderId) {
        invoker.executeCommand(new PrepareOrderCommand(orderId, orders, events, preparations));
    }

    /**
     * Collect payment for an order through {@code provider}'s Adapter. A gateway decline is returned
     * as a FAILED {@link PaymentResult} (and recorded), not thrown; a FAILED order can be paid again.
     *
     * @throws OrderStateConflictException if the order is not READY or is already PAID
     */
    public PaymentResult payOrder(Long orderId, PaymentProvider provider) {
        PayOrderCommand command = new PayOrderCommand(orderId, provider, gateways, orders, payments);
        invoker.executeCommand(command);
        return command.result();
    }

    /**
     * Fulfil an order: {@code READY -> FULFILLED} and bump the customer's fulfilled count, exactly
     * once. Requires a PAID payment.
     *
     * @throws OrderStateConflictException if the order has not been paid
     */
    public void fulfillOrder(Long orderId) {
        invoker.executeCommand(new FulfillOrderCommand(orderId, orders, events, users, payments));
    }

    /** Cancel an in-progress order. */
    public void cancelOrder(Long orderId) {
        invoker.executeCommand(new CancelOrderCommand(orderId, orders, events));
    }

    /**
     * Run an order through the rest of its lifecycle synchronously: prepare, pay, fulfil. Stops
     * after a FAILED payment without fulfilling: the returned outcome carries the FAILED result and
     * the order, still READY, so the caller can retry the payment.
     */
    public OrderOutcome processOrder(Long orderId, PaymentProvider provider) {
        prepareOrder(orderId);
        PaymentResult payment = payOrder(orderId, provider);
        if (payment.isPaid()) {
            fulfillOrder(orderId);
        }
        return new OrderOutcome(getOrder(orderId), payment);
    }

    /**
     * Re-order an existing order (Pattern 9: PROTOTYPE). Reads the persisted order (base coffee and
     * its ordered extras list), seeds a fresh prototype-scoped {@link OrderPrototype} from that
     * snapshot &mdash; never id/status/timestamps/price/tier &mdash; and re-places it, ending in
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

    /**
     * Undo the most recent lifecycle action, if any. A limited convenience, not a general reversal:
     * only a placement that is still PLACED, and a cancellation of a READY order, can be undone;
     * anything whose reversal would have effects outside the order row (payment, loyalty count,
     * preparation) throws {@code UndoNotSupportedException}.
     */
    public void undoLastAction() {
        invoker.undoLast();
    }
}
