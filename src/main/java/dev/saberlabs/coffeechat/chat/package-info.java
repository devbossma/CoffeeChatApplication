/**
 * Chat (Part 03 Step 5): the pure parsing and matching pieces ({@code OrderCommandParser},
 * {@code BaristaQueue}) and, in later slices, the persistence-facing store and the orchestrating
 * {@code ChatService}. The queue only decides matches; {@code ChatSessionStore} is the only writer of
 * {@code chat_sessions}; an order is only ever placed through {@code CoffeeShopFacade.placeOrder}.
 */
package dev.saberlabs.coffeechat.chat;
