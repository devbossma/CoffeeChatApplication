package dev.saberlabs.coffeechat.config;

import dev.saberlabs.coffeechat.singleton.CoffeeShop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AsyncConfig")
class AsyncConfigTest {

    @Nested
    @DisplayName("baristaTaskExecutor()")
    class BaristaTaskExecutorTests {

        @Test
        @DisplayName("sizes the pool (core and max) to CoffeeShop.baristaPoolSize()")
        void sizesToPoolSize() {
            CoffeeShop coffeeShop = new CoffeeShop(4);
            ThreadPoolTaskExecutor executor = new AsyncConfig().baristaTaskExecutor(coffeeShop);

            assertEquals(4, executor.getCorePoolSize());
            assertEquals(4, executor.getMaxPoolSize());
            executor.shutdown();
        }

        @Test
        @DisplayName("names its threads with the barista- prefix, for log correlation")
        void namesThreadsBaristaPrefix() throws InterruptedException {
            ThreadPoolTaskExecutor executor = new AsyncConfig().baristaTaskExecutor(new CoffeeShop(1));
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch ran = new CountDownLatch(1);

            executor.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                ran.countDown();
            });

            assertTrue(ran.await(1, TimeUnit.SECONDS));
            assertTrue(threadName.get().startsWith("barista-"));
            executor.shutdown();
        }

        @Test
        @DisplayName("interrupts a still-running task on shutdown instead of waiting for it to finish")
        void interruptsRunningTaskOnShutdown() throws InterruptedException {
            ThreadPoolTaskExecutor executor = new AsyncConfig().baristaTaskExecutor(new CoffeeShop(1));
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch interrupted = new CountDownLatch(1);

            executor.execute(() -> {
                started.countDown();
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            });

            assertTrue(started.await(1, TimeUnit.SECONDS));
            executor.shutdown();

            assertTrue(interrupted.await(2, TimeUnit.SECONDS),
                    "a blocked barista task should be interrupted promptly on shutdown, not awaited");
        }
    }
}
