# PRD: Not So Simple Chat — Coffee Shop on Spring

## 1. Summary

Port the coffee shop chat application from the plain-Java bootcamp project
(`MyDesignPattern`) onto Spring Boot, following the "Not So Simple Chat"
assignment (4 parts: design patterns, multithreading, chat + JDBC, testing).
This is a **new, separate repository** — not a fork or a copy-paste of the old
project's source. The old project stays as the reference for "what did we
already solve and why," and this one re-solves the same problems the Spring
way.

The point of the assignment isn't to reimplement 10 design patterns from
scratch. It's to see which of them Spring already gives you for free (a bean
*is* a singleton, DI *is* half of what a Factory or Facade was doing by hand),
which ones need a real Spring-native replacement (manual threads and
`Observable` become `@Async` and `ApplicationEventPublisher`), and which ones
don't map onto Spring cleanly at all (Prototype, mostly) and need an honest
call on how far to force it.

## 2. Background

`MyDesignPattern` already built this exact application in plain Java across
five phases: 10 GoF patterns, a multithreaded producer/consumer order
pipeline, a chat feature backed by raw JDBC/SQLite, a full JUnit test suite,
and (as an extra, out-of-scope-here fifth phase) a small reflection-based
dispatch framework. Every functional decision in that project — loyalty tier
thresholds, order status lifecycle, how a chat session gets matched to a
barista, how an order command is told apart from small talk — was already
argued through once. Section 6 carries those decisions forward explicitly so
this PRD doesn't re-litigate them; it only decides what changes because the
runtime is now Spring instead of a plain JVM.

Reference: `../MyDesignPattern` (`docs/*.md`, `src/main/java/dev/saberlabs/**/doc.md`).

## 3. Goals

- Reimplement the coffee shop domain (coffee types, extras, pricing,
  ordering, notifications) on Spring Boot, using Spring's own mechanisms
  wherever they genuinely replace what the pattern was doing manually.
- Support concurrent order placement and asynchronous barista preparation
  using `@Async` and Spring's concurrency utilities.
- Add a chat feature: customers and baristas exchange messages, order
  commands can be issued through chat, and the conversation history is
  persisted via Spring Data JPA.
- Expose the application through a REST API (`@RestController`), since the
  assignment's own "Example Usage" is a REST controller, not a UI.
- Cover the app with JUnit 5 + Mockito unit tests and Spring Boot integration
  tests (`@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`), matching the
  assignment's own `OrderQueueTest` / `ChatServiceTest` examples.

## 4. Non-Goals

- **No JavaFX / CLI UI.** The old project had both; this one is API-only,
  per the assignment's own framing ("REST controller for customer
  interactions"). A UI can be a later, separate concern.
- **No reflection-dispatch framework.** The `BusinessObject` /
  `InteractionHandler` layer from `MyDesignPattern`'s fifth phase isn't part
  of this assignment's four parts and won't be ported.
- **No full authentication/authorization system.** The assignment lists
  "consider user authentication" under *Additional Notes*, i.e. optional. A
  minimal `Role` enum on the customer/barista model is in scope; Spring
  Security, JWTs, or login flows are not, unless explicitly requested later.
- **No payment gateway integration beyond the Adapter pattern's shape.** As
  in the old project, `PayPalAdapter` / `StripeAdapter` /
  `CashPaymentAdapter` simulate a gateway; no real payment processor is
  called.

## 5. Scope (mapped to the assignment's 4 parts)

| Part | Assignment title | What this PRD calls it below |
|---|---|---|
| 01 | Reimplement Design Patterns | §7 Domain & Patterns |
| 02 | Multithreading with Spring | §8 Concurrency |
| 03 | Chat Functionality with Spring and JDBC | §9 Chat & Persistence |
| 04 | Testing with Spring Boot and JUnit | §10 Testing |

## 6. Decisions carried over from `MyDesignPattern`

These are settled; re-derive them in Spring terms, don't redesign them.

| Decision | Value | Source |
|---|---|---|
| Order lifecycle | `PLACED → PREPARING → READY → FULFILLED`, plus `CANCELLED` | `models.OrderStatus` |
| Loyalty tiers | `REGULAR` 0–5 fulfilled orders (0% off) → `SILVER` 6–10 (10% off) → `GOLD` 11+ (20% off) | `models.LoyaltyTier` |
| Coffee types | Espresso, Cappuccino, Latte (Factory Method products) | `factory/`, `models/` |
| Extras | Milk, Sugar, Whipped Cream (Decorator) | `decorator/` |
| Roles | `CUSTOMER`, `BARISTA`, `MANAGER` | `auth.Role` |
| Chat message shape | type (`CHAT_MESSAGE` / `SYSTEM_MESSAGE`), session id, sender id/name, content, timestamp, optional order id | `chat.ChatMessage` |
| Chat session lifecycle | `WAITING → ACTIVE → INACTIVE`, FIFO matching (oldest-waiting customer ↔ oldest-ready barista) | `chat.ChatSession`, `chat.BaristaQueue` |
| Order-command marker | An explicit `/order <coffee> [extras...]` prefix distinguishes a real order from free chat text (a bare `"order"` prefix false-positives on sentences like "order latte from this place was amazing") | `framework/doc.md`, `chat.OrderCommandParser` |
| Command history | Every order-lifecycle command (`Place`/`Prepare`/`Pay`/`Fulfill`) runs through an invoker that keeps a history, not called ad hoc | `command/` |

## 7. Domain & Patterns (Part 01)

### 7.1 Package layout

Following the assignment's mandated structure:

```
dev.saberlabs.coffeechat
├── factory/       CoffeeFactory (@Service), one method per coffee type
├── observer/       OrderStatusChangedEvent + listeners (ApplicationEventPublisher)
├── strategy/       PricingStrategy + Regular/Silver/GoldMemberPricing (@Component)
├── decorator/      Coffee, CoffeeDecorator, Milk/Sugar/WhippedCreamDecorator
├── singleton/      CoffeeShop (@Component, effectively a singleton bean)
├── command/        Command interface, Place/Prepare/Pay/FulfillOrderCommand, OrderInvoker
├── adapter/        PaymentGateway + PayPal/Stripe/CashAdapter (@Component)
├── facade/         CoffeeShopFacade (@Service)
├── prototype/      OrderPrototype (@Scope("prototype"))
├── template/       CoffeePreparationTemplate + per-coffee subclasses
├── model/          Order, Customer, Coffee, OrderStatus, LoyaltyTier, Role
├── controller/      REST controllers (order, chat)
├── service/        OrderService, ChatService, NotificationService
└── repository/      Spring Data JPA repositories (chat, orders — see §9)
```

### 7.2 Pattern-by-pattern: what changes under Spring

| # | Pattern | Old approach (`MyDesignPattern`) | Spring approach here |
|---|---|---|---|
| 1 | Singleton | Hand-written double-checked locking in `CoffeeShop` | `@Component` — Spring's default bean scope *is* singleton. Delete the manual locking entirely; it's dead code once the container owns the instance. |
| 2 | Factory Method | One `Creator` class per coffee type (`EspressoCreator`, ...) implementing a common `CoffeeCreator` | A single `CoffeeFactory` `@Service` (assignment's own wording) with one method per type, or a `Map<CoffeeType, Supplier<Coffee>>` built from injected per-type beans — pick whichever keeps `switch`-free branching; document the choice in code, not just here. |
| 3 | Observer | Hand-rolled `Observable`/`OrderObserver` + `CopyOnWriteArraySet` | `ApplicationEventPublisher.publishEvent(new OrderStatusChangedEvent(...))` from `OrderService`; `@EventListener` methods replace `OrderObserver` implementations. Spring's event bus **is** the Observer pattern — don't build a second one beside it. |
| 4 | Strategy | `LoyaltyTier` enum maps to a `Supplier<PricingStrategy>` | Each tier's strategy becomes a `@Component`; inject a `Map<LoyaltyTier, PricingStrategy>` (Spring auto-populates a `Map<String, T>` of bean name → bean for any interface with multiple implementations — key it by an explicit bean name/qualifier per tier) instead of the enum owning a `Supplier`. |
| 5 | Decorator | Plain OOP wrapping (`new SugarDecorator(new MilkDecorator(espresso))`) | Stays plain OOP. A decorated coffee is a *value*, constructed once per order — it doesn't belong in the Spring container as a bean. `CoffeeFactory` builds the base coffee; the order-building service applies decorators. Note this explicitly in the code so it's clear the omission is deliberate, not a miss. |
| 6 | Command | `Command` interface, 4 concrete commands, `OrderInvoker` keeping history | Same shape. `OrderInvoker` becomes a `@Service`; wrap the order-placement command's execution in `@Transactional` per the assignment, since it now touches a real database (chat/order persistence, §9) instead of an in-memory list. |
| 7 | Adapter | `PaymentGateway` + `PayPalAdapter`/`StripeAdapter`/`CashPaymentAdapter` | Same interface + adapters, each a `@Component`; select which one `CoffeeShopFacade` gets via constructor injection (a single active adapter bean, or `@Qualifier` if more than one is registered at once). |
| 8 | Facade | `CoffeeShopFacade` wrapping `OrderService` | Same role, `@Service`. This is the class controllers call — controllers don't reach into `OrderService`/`CoffeeFactory` directly. |
| 9 | Prototype | `CloneableOrder`/`CloneableCoffee` implementing a copy method | Use Spring's actual prototype scope this time (the assignment specifically asks for it): an `OrderPrototype` bean declared `@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)`, fetched via `ObjectProvider<OrderPrototype>` when a "reorder" needs a fresh, independent copy to customize. This is a genuine change from the old approach, not just a rename — call it out in the README as the one pattern that only really makes sense once Spring is involved. |
| 10 | Template Method | `CoffeePreparationTemplate` abstract class + per-coffee subclasses | Unchanged — Spring has no special idiom for Template Method beyond what plain Java already does. Subclasses can still be `@Component`s if `CoffeeFactory` wants to inject them, but the pattern itself doesn't need the container. |

## 8. Concurrency (Part 02)

| Old approach | Spring approach |
|---|---|
| `CoffeeShop.open()` manually starts N `Barista` `Thread`s pulling from a `BlockingQueue`-backed `OrderQueue` | Keep `OrderQueue` as a thread-safe `BlockingQueue<Order>`-backed component (the assignment explicitly asks for this shape), but replace manually-started `Thread`s with `@Async` methods on a `Barista` `@Service`, backed by a configured `ThreadPoolTaskExecutor` bean (`@EnableAsync`, a `@Bean TaskExecutor` with a bounded pool + queue capacity). |
| `Barista.run()` loop blocks on `orderQueue.dequeue()` | An `@Async` `prepareOrder(Order)` method is invoked once per dequeued order (a small `@Scheduled` or event-driven poller pulls from the queue and dispatches), instead of one thread owning an infinite loop. Evaluate both against the assignment's "process orders asynchronously using `@Async`" wording before committing — the poller shape is likely the more idiomatic fit. |
| Order status change calls `notifyObservers` directly | Order status change publishes `OrderStatusChangedEvent`; a listener sends the "order ready" notification. Async listeners (`@Async @EventListener`) if the notification itself shouldn't block the preparation thread. |
| `CoffeeShop`'s synchronized order list + `AtomicInteger` id counters | Kept conceptually (thread-safe order tracking still needed), but once orders are JPA-backed (§9), persistence itself gives most of the safety; keep in-memory counters only where the DB doesn't already provide an equivalent (e.g. a DB sequence/identity column replaces `orderIdCounter`). |

## 9. Chat & Persistence (Part 03)

### 9.1 Database

**PostgreSQL**, configured via Spring Data JPA (`application.properties` /
`application.yml`; a local Postgres instance or Docker Compose service for
development). This is a deliberate departure from the old project's SQLite —
Spring Data JPA's dialect support and the assignment's own JDBC framing are a
better fit for a real client-server database than continuing single-file
SQLite.

### 9.2 Entities

| Entity | Carries forward from | Notes |
|---|---|---|
| `ChatSessionEntity` | `chat.ChatSession` (record) | `id`, `customerId`, `baristaId` (nullable), `status` (`WAITING`/`ACTIVE`/`INACTIVE`), `createdAt`. Becomes a real `@Entity` with a generated id instead of a record built by a factory method. |
| `ChatMessageEntity` | `chat.ChatMessage` (record) | `id`, `type` (`CHAT_MESSAGE`/`SYSTEM_MESSAGE`), `sessionId` (FK), `senderId`, `senderName`, `content`, `timestamp`, `orderId` (nullable). |
| `OrderEntity` | `models.Order` | Persisted instead of held only in `CoffeeShop`'s in-memory list; `status`, `customerId`, coffee description, price, timestamps. |
| `CustomerEntity` | `models.Customer` | `id`, `name`, `loyaltyTier` (or a derived fulfilled-order count), `totalOrders`. |

Repositories are plain Spring Data JPA interfaces
(`ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long>`,
etc.) — no hand-written SQL, no `DatabaseUtil`-style manual connection
management. That whole class disappears; Spring Boot's
`DataSource`/`EntityManager` auto-configuration replaces it outright.

### 9.3 Chat flow

- `POST /api/chat/sessions` — start a session for a customer (`WAITING`,
  matched to a ready barista if one exists — port `BaristaQueue`'s FIFO
  matching logic as a `@Component`, kept in-memory since it's live
  coordination state, not history).
- `POST /api/chat/sessions/{id}/messages` — send a message. Carries forward
  the `/order <coffee> [extras...]` marker convention (§6) to tell an order
  command apart from small talk before persisting it and, if it's an order,
  handing it to `OrderService`/`CoffeeShopFacade`.
- `GET /api/chat/sessions/{id}/messages` — load history, ordered by
  timestamp, via the JPA repository.

### 9.4 REST surface (Part 01 + 03 combined)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/customers` | Create a customer |
| `POST` | `/api/orders` | Place an order (Facade → Command → Factory/Decorator/Strategy) |
| `GET` | `/api/orders/{id}` | Order status |
| `POST` | `/api/orders/{id}/reorder` | Prototype-clone an existing order |
| `POST` | `/api/chat/sessions` | Start a chat session |
| `POST` | `/api/chat/sessions/{id}/messages` | Send a chat message / order command |
| `GET` | `/api/chat/sessions/{id}/messages` | Load chat history |

## 10. Testing (Part 04)

- **Unit tests** (JUnit 5 + Mockito): one test class per service/component,
  following the assignment's own `OrderQueueTest` / `ChatServiceTest`
  examples almost directly — `@Mock` the repository, `@InjectMocks` the
  service, verify interactions.
- **Web layer tests**: `@WebMvcTest` per controller, `MockMvc` for the REST
  surface in §9.4, mocked service layer underneath.
- **Persistence tests**: `@DataJpaTest` for the Spring Data JPA repositories
  against an embedded/test Postgres (Testcontainers, if available; otherwise
  a dedicated test schema).
- **Integration tests**: `@SpringBootTest` exercising a full order-placement
  and full chat-session flow end to end, mirroring
  `CoffeeShopIntegrationTest` from the old project.
- Coverage target: match the old project's discipline (a real per-package
  coverage gate, not just "some tests exist") — exact tool/threshold
  (JaCoCo, as before) to be wired once the project is scaffolded.

## 11. Explicit open questions

These are real decisions still pending, deliberately not pre-answered here:

1. **Async dispatch shape.** Whether `Barista` order preparation is
   `@Async`-triggered directly per dequeued order or driven by a
   `@Scheduled` poller reading `OrderQueue` (§8) — needs a short spike
   against Spring's actual `@Async` semantics before locking in.
2. **Strategy bean keying.** Whether `Map<LoyaltyTier, PricingStrategy>`
   injection is keyed by enum-matching bean names or an explicit
   `@Qualifier`/custom annotation per tier (§7.2, row 4).
3. **Auth scope.** Whether the `Role` enum needs any enforcement at all in
   the MVP (e.g. a barista-only endpoint) or purely models identity for
   sender attribution in chat, per the non-goal in §4.
4. **Project coordinates.** Group id / artifact id / base package for the
   new repo — this PRD assumes `dev.saberlabs.coffeechat` for continuity;
   confirm before running Spring Initializr.

## 12. Milestones

1. Scaffold: Spring Initializr project, package skeleton (§7.1), empty
   `pom.xml`/`build.gradle`, Postgres connection wired, health check.
2. Part 01: domain model + all 10 patterns per §7.2, no persistence yet
   (in-memory), REST controller for order placement.
3. Part 02: `@Async` barista preparation + `OrderQueue`, event-driven
   notifications.
4. Part 03: JPA entities/repositories, chat REST endpoints, order commands
   through chat.
5. Part 04: test suite across all four parts, coverage gate wired.

## 13. Appendix

- Assignment source: "Not So Simple Chat" (verbatim in this session).
- Reference implementation: `../MyDesignPattern` — see its root `README.md`
  and `docs/*.md` for the plain-Java version's own reasoning, especially
  `docs/DESIGN-PATTERNS-README.md`, `docs/MULTITHREADING-README.md`, and
  `docs/COFFEE-CHAT-README.md`.
