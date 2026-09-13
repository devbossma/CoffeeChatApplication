package dev.saberlabs.coffeechat.config;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and defines the dedicated executor the Part 02 Barista consumer loops
 * run on (PRD &sect;11.1 / {@code CLAUDE.md} resolved decision #1: N {@code @Async} consumer
 * loops backed by a {@code ThreadPoolTaskExecutor}, not a {@code @Scheduled} poller).
 *
 * <p>Sized to exactly {@link CoffeeShop#baristaPoolSize()} (core == max): every barista loop is a
 * long-lived blocking task for the lifetime of the app, so there is no queueing of overflow work
 * on this executor and no reason for elastic sizing. {@code setWaitForTasksToCompleteOnShutdown(false)}
 * means a context shutdown interrupts a barista parked in {@code OrderQueue.take()} instead of
 * waiting on it forever.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("baristaTaskExecutor")
    public ThreadPoolTaskExecutor baristaTaskExecutor(CoffeeShop coffeeShop) {
        int poolSize = coffeeShop.baristaPoolSize();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("barista-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
