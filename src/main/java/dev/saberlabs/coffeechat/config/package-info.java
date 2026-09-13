/**
 * Cross-cutting Spring configuration. {@code AsyncConfig} enables {@code @Async} and defines the
 * dedicated {@code ThreadPoolTaskExecutor} the Part 02 Barista consumer loops run on, sized from
 * {@code CoffeeShop.baristaPoolSize()} so it never competes for threads with unrelated
 * {@code @Async} work added later.
 */
package dev.saberlabs.coffeechat.config;
