package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts the Barista consumer pool once the application is fully up, per the resolved concurrency
 * decision (PRD &sect;11.1 / {@code CLAUDE.md}): N {@code @Async} consumer loops, N =
 * {@link CoffeeShop#baristaPoolSize()}, each started via
 * {@code @EventListener(ApplicationReadyEvent.class)} and blocking on {@code OrderQueue.take()}.
 *
 * <p>This has to be a separate bean from {@link Barista}: Spring's {@code @Async} proxy only
 * intercepts calls that come in from <em>outside</em> the bean, so {@code Barista} cannot start
 * its own loops via a plain {@code this.consumeLoop()} self-call.
 */
@Component
public class BaristaSupervisor {

    private static final Logger log = LoggerFactory.getLogger(BaristaSupervisor.class);

    private final Barista barista;
    private final CoffeeShop coffeeShop;

    public BaristaSupervisor(Barista barista, CoffeeShop coffeeShop) {
        this.barista = barista;
        this.coffeeShop = coffeeShop;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startBaristas() {
        int poolSize = coffeeShop.baristaPoolSize();
        log.info("Starting {} barista consumer loop(s)", poolSize);
        for (int i = 0; i < poolSize; i++) {
            barista.consumeLoop();
        }
    }

    @PreDestroy
    public void stopBaristas() {
        barista.shutdown();
    }
}
