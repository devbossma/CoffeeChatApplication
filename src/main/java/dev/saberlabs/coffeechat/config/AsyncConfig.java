package dev.saberlabs.coffeechat.config;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Objects;

/**
 * Enables {@code @Async} and defines the dedicated executor the Part 02 Barista consumer loops
 * run on (PRD &sect;11.1 / {@code CLAUDE.md} resolved decision #1: N {@code @Async} consumer
 * loops backed by a {@code ThreadPoolTaskExecutor}, not a {@code @Scheduled} poller).
 *
 * <p>Sized to exactly {@link CoffeeShop#baristaPoolSize()} (core == max): every barista loop is a
 * long-lived blocking task for the lifetime of the app, so there is no queueing of overflow work
 * on this executor and no reason for elastic sizing. {@code setWaitForTasksToCompleteOnShutdown(false)}
 * means a context shutdown interrupts a barista parked in {@code OrderQueue.take()} instead of
 * waiting on it forever &mdash; though the real fix for the graceful-shutdown stall this executor
 * would otherwise sit through is {@code BaristaSupervisor.onContextClosed()}, see its javadoc.
 *
 * <p>{@code setDaemon(true)} is defensive insurance, not a fix: even if some future change
 * reintroduces a shutdown path that fails to interrupt these threads, a daemon thread can never
 * by itself keep the JVM (or a test runner's forked JVM) from exiting.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("baristaTaskExecutor")
    public ThreadPoolTaskExecutor baristaTaskExecutor(@NotNull CoffeeShop coffeeShop) {
        Objects.requireNonNull(coffeeShop, "coffeeShop cannot be null");
        int poolSize = coffeeShop.baristaPoolSize();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("barista-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
