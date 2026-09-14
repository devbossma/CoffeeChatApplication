package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Starts the Barista consumer pool once the application is fully up, per the resolved concurrency
 * decision (PRD &sect;11.1 / {@code CLAUDE.md}): N {@code @Async} consumer loops, N =
 * {@link CoffeeShop#baristaPoolSize()}, each started via
 * {@code @EventListener(ApplicationReadyEvent.class)} and blocking on {@code OrderQueue.take()}.
 *
 * <p>This has to be a separate bean from {@link Barista}: Spring's {@code @Async} proxy only
 * intercepts calls that come in from <em>outside</em> the bean, so {@code Barista} cannot start
 * its own loops via a plain {@code this.consumeLoop()} self-call.
 *
 * <p><b>Why {@link #onContextClosed()} calls {@code shutdownNow()} directly</b> (not just
 * {@code @PreDestroy}): since Spring Framework 6.1, {@code ThreadPoolTaskExecutor} is itself a
 * {@code SmartLifecycle} whose coordinated {@code stop(Runnable)} only fires its callback once
 * every currently-running task has returned on its own. {@code consumeLoop()} is a deliberately
 * infinite task &mdash; it never returns by itself &mdash; so that callback would never fire, and
 * {@code DefaultLifecycleProcessor} would block for the full per-phase shutdown timeout (Spring
 * Boot defaults {@code spring.lifecycle.timeout-per-shutdown-phase} to 30s) on every context
 * close before falling back to {@code DisposableBean.destroy()}'s {@code shutdownNow()}. Spring
 * publishes {@code ContextClosedEvent} to plain listeners like this one <em>before</em> that
 * coordinated stop phase runs ({@code AbstractApplicationContext.doClose()}), so interrupting the
 * barista threads here means {@code executingTaskCount} is already back to 0 by the time the
 * stop phase checks it &mdash; no stall, not just a shorter one.
 */
@Component
public class BaristaSupervisor {

    private static final Logger log = LoggerFactory.getLogger(BaristaSupervisor.class);

    private final Barista barista;
    private final CoffeeShop coffeeShop;
    private final ThreadPoolTaskExecutor baristaTaskExecutor;

    public BaristaSupervisor(@NotNull Barista barista,
                             @NotNull CoffeeShop coffeeShop,
                             @NotNull @Qualifier("baristaTaskExecutor") ThreadPoolTaskExecutor baristaTaskExecutor) {
        this.barista = Objects.requireNonNull(barista, "barista cannot be null");
        this.coffeeShop = Objects.requireNonNull(coffeeShop, "coffeeShop cannot be null");
        this.baristaTaskExecutor = Objects.requireNonNull(baristaTaskExecutor, "baristaTaskExecutor cannot be null");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startBaristas() {
        int poolSize = coffeeShop.baristaPoolSize();
        log.info("Starting {} barista consumer loop(s)", poolSize);
        for (int i = 0; i < poolSize; i++) {
            barista.consumeLoop();
        }
    }

    /** The real fix: interrupt blocked baristas before Spring's coordinated stop phase can stall on them. */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        barista.shutdown();
        baristaTaskExecutor.getThreadPoolExecutor().shutdownNow();
    }

    /** Defensive fallback for a bean-destroy path that doesn't go through a full context close. */
    @PreDestroy
    public void stopBaristas() {
        barista.shutdown();
    }
}
