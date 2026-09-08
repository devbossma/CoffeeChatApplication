package dev.saberlabs.coffeechat.singleton;

import dev.saberlabs.coffeechat.model.CoffeeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pattern 1: SINGLETON.
 *
 * <p>Spring already guarantees one instance of a {@code @Component}, so the hand-written
 * double-checked locking from {@code MyDesignPattern} is gone. What is <em>not</em> redundant is
 * this bean's job: shop-wide operational state that is inherently singular and belongs on no
 * per-order or per-customer row ({@code CLAUDE.md}):
 *
 * <ul>
 *   <li>whether the shop is currently accepting orders ({@link #isOpen()});</li>
 *   <li>the active menu &mdash; which {@link CoffeeType}s are being served right now;</li>
 *   <li>the barista pool size, read once at startup.</li>
 * </ul>
 *
 * <p>It holds <strong>no</strong> order list and <strong>no</strong> id counters &mdash; those
 * would be a second source of truth against {@code OrderService} / the Part 03 repository.
 * {@code CoffeeShopFacade.placeOrder(...)} consults {@link #isOpen()} and {@link #isOnMenu} before
 * it does anything else.
 */
@Component
public class CoffeeShop {

    private final AtomicBoolean open = new AtomicBoolean(true);
    private final Set<CoffeeType> activeMenu =
            Collections.synchronizedSet(EnumSet.allOf(CoffeeType.class));
    private final int baristaPoolSize;

    public CoffeeShop(@Value("${coffeeshop.barista-pool-size:3}") int baristaPoolSize) {
        if (baristaPoolSize < 1) {
            throw new IllegalArgumentException("barista pool size must be at least 1: " + baristaPoolSize);
        }
        this.baristaPoolSize = baristaPoolSize;
    }

    /** @return whether new orders are currently accepted. */
    public boolean isOpen() {
        return open.get();
    }

    /** Starts accepting new orders. Idempotent. */
    public void open() {
        open.set(true);
    }

    /** Stops accepting new orders. Idempotent. */
    public void close() {
        open.set(false);
    }

    /** @return whether {@code type} is on the active menu right now. */
    public boolean isOnMenu(CoffeeType type) {
        return type != null && activeMenu.contains(type);
    }

    /** An immutable snapshot of the active menu. */
    public Set<CoffeeType> activeMenu() {
        synchronized (activeMenu) {
            return Set.copyOf(activeMenu);
        }
    }

    /** Adds a type to the active menu. Idempotent. */
    public void serve(CoffeeType type) {
        activeMenu.add(type);
    }

    /** Removes a type from the active menu. Idempotent. */
    public void stopServing(CoffeeType type) {
        activeMenu.remove(type);
    }

    public int baristaPoolSize() {
        return baristaPoolSize;
    }

    /**
     * Restores the default operational state (open, full menu). Intended for resetting the
     * singleton bean between integration tests, the way {@code MyDesignPattern} reset
     * {@code CoffeeShop.getInstance()}.
     */
    public void reset() {
        open.set(true);
        synchronized (activeMenu) {
            activeMenu.clear();
            activeMenu.addAll(EnumSet.allOf(CoffeeType.class));
        }
    }
}