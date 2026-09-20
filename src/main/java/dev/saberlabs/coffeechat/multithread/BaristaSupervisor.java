package dev.saberlabs.coffeechat.multithread;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts the Barista consumer pool once the application is fully up, per the resolved concurrency
 * decision (PRD &sect;11.1 / {@code CLAUDE.md}): N {@code @Async} consumer loops, N =
 * {@link CoffeeShop#baristaPoolSize()}, each launched once as a {@code SmartLifecycle} auto-start
 * (after the context has finished refreshing, and again if a paused context is resumed) rather
 * than from an {@code ApplicationReadyEvent} listener &mdash; see below for why.
 *
 * <p>This has to be a separate bean from {@link Barista}: Spring's {@code @Async} proxy only
 * intercepts calls that come in from <em>outside</em> the bean, so {@code Barista} cannot start
 * its own loops via a plain {@code this.consumeLoop()} self-call.
 *
 * <p><b>Why this is a {@link SmartLifecycle}</b> (root cause of the 30s "Shutdown phase ...
 * [baristaTaskExecutor]" stall): since Spring Framework 6.1, {@code ThreadPoolTaskExecutor} is
 * itself a {@code SmartLifecycle} whose coordinated {@code stop()} only completes once every
 * running task has returned. {@code consumeLoop()} is a deliberately long-lived task, so unless
 * something tells it to stop <em>before</em> the executor's stop phase begins,
 * {@code DefaultLifecycleProcessor} waits the full per-phase timeout (30s by default). An earlier
 * version tried to handle this in a {@code ContextClosedEvent} listener, but that never fires in
 * the case that mattered: Spring Framework 7's test-context cache <em>pauses</em> an idle
 * context (a lifecycle {@code stop()}, no close) whenever a different context is about to start,
 * so the listener was skipped and every paused full context cost 30s. Participating in the
 * lifecycle directly covers both a close and a pause. The phase is one <em>higher</em> than the
 * executor's, and lifecycle beans stop in descending phase order, so the baristas are told to stop
 * before the executor starts waiting on them; {@link Barista#consumeLoop()} then exits within one
 * poll interval because it checks its stop flag on a timed poll rather than blocking indefinitely.
 * On resume the executor (lower phase) starts first, then {@link #start()} re-launches the loops.
 */
@Component
public class BaristaSupervisor implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BaristaSupervisor.class);

    /** {@code ThreadPoolTaskExecutor}'s own lifecycle phase is {@code Integer.MAX_VALUE / 2}; one above stops first. */
    private static final int PHASE = Integer.MAX_VALUE / 2 + 1;

    private final Barista barista;
    private final CoffeeShop coffeeShop;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public BaristaSupervisor(@NotNull Barista barista, @NotNull CoffeeShop coffeeShop) {
        this.barista = Objects.requireNonNull(barista, "barista cannot be null");
        this.coffeeShop = Objects.requireNonNull(coffeeShop, "coffeeShop cannot be null");
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        barista.restart();
        int poolSize = coffeeShop.baristaPoolSize();
        log.info("Starting {} barista consumer loop(s)", poolSize);
        for (int i = 0; i < poolSize; i++) {
            barista.consumeLoop();
        }
    }

    /** Runs on a context close <em>and</em> on a test-context pause; see the class javadoc. */
    @Override
    public void stop() {
        log.info("Stopping barista consumer loop(s)");
        started.set(false);
        barista.shutdown();
    }

    @Override
    public boolean isRunning() {
        return started.get();
    }

    /**
     * Must be {@code true}: Spring's context resume (a test-context cache un-pausing a cached
     * context) only restarts auto-startup lifecycle beans, so an {@code ApplicationReadyEvent}
     * listener alone would start the loops once and never again after a pause/resume, leaving a
     * reused context with no baristas (observed: an order stuck in {@code PLACED}).
     */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    /** Defensive fallback for a bean-destroy path that doesn't go through the lifecycle processor. */
    @PreDestroy
    public void stopBaristas() {
        stop();
    }
}
