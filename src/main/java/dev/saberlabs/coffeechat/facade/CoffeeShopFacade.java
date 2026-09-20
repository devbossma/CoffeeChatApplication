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
import dev.saberlabs.coffeechat.service.StaffAccess;
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
    private final StaffAccess staffAccess;
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
                            StaffAccess staffAccess,
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
        this.staffAccess = staffAccess;
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
     * <p>Known limitation: the tier is read just before the command's transaction, so a fulfilment
     * that commits in that gap can leave the frozen tier one order stale. Tier and price are always
     * consistent with each other, and the next order picks up the new tier.
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

    /**
     * Prepare an order: Template Method recipe, then {@code PLACED -> READY}. A status transition:
     * {@code actor} must be a BARISTA or MANAGER (or {@link Actor#SYSTEM}, the async barista loops).
     *
     * @throws UnknownActorException   if the actor's user id does not exist (401)
     * @throws RoleNotAllowedException if the actor is not staff (403)
     */
    public void prepareOrder(Long orderId, Actor actor) {
        invoker.executeCommand(new PrepareOrderCommand(orderId, orders, events, preparations, actor, staffAccess));
    }

    /**
     * Collect payment for an order through {@code provider}'s Adapter. Allowed for staff (collecting at
     * the counter), the order's own CUSTOMER, or {@link Actor#SYSTEM}; a different customer paying
     * someone else's order is rejected before the gateway is called. A gateway decline is returned as a
     * FAILED {@link PaymentResult} (and recorded), not thrown; a FAILED order can be paid again.
     *
     * @throws UnknownActorException       if the actor's user id does not exist (401)
     * @throws RoleNotAllowedException     if the actor may not pay this order (403)
     * @throws OrderStateConflictException if the order is not READY or is already PAID
     */
    public PaymentResult payOrder(Long orderId, PaymentProvider provider, Actor actor) {
        PayOrderCommand command = new PayOrderCommand(orderId, provider, gateways, orders, payments, actor, staffAccess);
        invoker.executeCommand(command);
        return command.result();
    }

    /**
     * Fulfil an order: {@code READY -> FULFILLED} and bump the customer's fulfilled count, exactly
     * once. Requires a PAID payment and a staff actor (BARISTA, MANAGER) or {@link Actor#SYSTEM}.
     *
     * @throws UnknownActorException       if the actor's user id does not exist (401)
     * @throws RoleNotAllowedException     if the actor is not staff (403)
     * @throws OrderStateConflictException if the order has not been paid
     */
    public void fulfillOrder(Long orderId, Actor actor) {
        invoker.executeCommand(new FulfillOrderCommand(orderId, orders, events, users, payments, actor, staffAccess));
    }

    /**
     * Cancel an in-progress order. A status transition: staff (BARISTA, MANAGER) or
     * {@link Actor#SYSTEM} only. Known limitation: customers cannot cancel their own order through the
     * API yet.
     *
     * @throws UnknownActorException   if the actor's user id does not exist (401)
     * @throws RoleNotAllowedException if the actor is not staff (403)
     */
    public void cancelOrder(Long orderId, Actor actor) {
        invoker.executeCommand(new CancelOrderCommand(orderId, orders, events, actor, staffAccess));
    }

    /**
     * Run an order through the rest of its lifecycle synchronously: prepare, pay, fulfil, all as
     * {@code actor} (so it needs staff or {@link Actor#SYSTEM}). Stops after a FAILED payment without
     * fulfilling: the returned outcome carries the FAILED result and the order, still READY, so the
     * caller can retry the payment.
     */
    public OrderOutcome processOrder(Long orderId, PaymentProvider provider, Actor actor) {
        prepareOrder(orderId, actor);
        PaymentResult payment = payOrder(orderId, provider, actor);
        if (payment.isPaid()) {
            fulfillOrder(orderId, actor);
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
     * preparation) throws {@code UndoNotSupportedException}. It is a status transition, so it needs a
     * staff actor (or {@link Actor#SYSTEM}), and the changes it makes are attributed to that actor.
     *
     * @throws UnknownActorException   if the actor's user id does not exist (401)
     * @throws RoleNotAllowedException if the actor is not staff (403)
     */
    public void undoLastAction(Actor actor) {
        Long recordedActor = staffAccess.authorize(actor, StaffAccess.STAFF);
        invoker.undoLast(recordedActor);
    }

    /**
     * Opens the shop for new orders. Shop-wide operational state, so only a MANAGER may change it.
     *
     * @throws UnknownActorException   if the actor's user id does not exist (401)
     * @throws RoleNotAllowedException if the actor is not a MANAGER (403)
     */
    public void openShop(Actor actor) {
        staffAccess.authorize(actor, java.util.EnumSet.of(dev.saberlabs.coffeechat.model.Role.MANAGER));
        coffeeShop.open();
    }

    /** Closes the shop to new orders (orders already placed continue). Same rules as {@link #openShop}. */
    public void closeShop(Actor actor) {
        staffAccess.authorize(actor, java.util.EnumSet.of(dev.saberlabs.coffeechat.model.Role.MANAGER));
        coffeeShop.close();
    }
}
