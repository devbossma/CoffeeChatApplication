/**
 * Part 03: JPA {@code @Entity} classes, persisted against the Flyway-owned schema in
 * {@code src/main/resources/db/migration}.
 *
 * <p>Deliberately separate from {@code dev.saberlabs.coffeechat.model}'s plain domain objects
 * ({@code Order}, {@code Customer}, ...): those carry business logic used directly by the Command
 * pattern ({@code Order.transitionTo}, {@code Customer.loyaltyTier}) and are persistence-agnostic
 * by design, while entities are a pure persistence-layer representation. Part 03 Step 3 maps
 * between the two behind {@code OrderService}/{@code CustomerService} once those become
 * JPA-backed; the domain model itself does not change shape to accommodate JPA.
 *
 * <p>{@code CustomerEntity} does not exist as a separate class: it is merged into
 * {@link dev.saberlabs.coffeechat.entity.UserEntity} (see PRD &sect;9.2 and the Part 03 schema
 * proposal) &mdash; one identity table for every person (customer, barista, manager), with
 * {@code fulfilledOrders} meaningful only for {@code CUSTOMER} rows, rather than two disconnected
 * id spaces for the same person.
 */
package dev.saberlabs.coffeechat.entity;
