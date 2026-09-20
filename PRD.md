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
├── model/          Order (immutable snapshot), Coffee, PriceBreakdown, OrderStatus, LoyaltyTier, Role
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
| `Barista.run()` loop blocks on `orderQueue.dequeue()` | Resolved (CLAUDE.md, PRD 11.1): N `@Async` consumer loops on a bounded `ThreadPoolTaskExecutor`, one per pool slot, each waiting on a *timed* poll of the queue so a stop flag is noticed without an interrupt; not a `@Scheduled` poller. See 8.1 for the as-built behaviour. |
| Order status change calls `notifyObservers` directly | Order status change publishes `OrderStatusChangedEvent`; a listener sends the "order ready" notification. As built (9.5): the audit listener runs inside the transaction; notification and queueing are `AFTER_COMMIT`. |
| `CoffeeShop`'s synchronized order list + `AtomicInteger` id counters | Kept conceptually (thread-safe order tracking still needed), but once orders are JPA-backed (§9), persistence itself gives most of the safety; keep in-memory counters only where the DB doesn't already provide an equivalent (e.g. a DB sequence/identity column replaces `orderIdCounter`). |

### 8.1 As built (after Part 03 Step 3)

The table above records the design questions; this is what the code does now.

- **`OrderQueue` carries order ids, not objects** (`BlockingQueue<Long>`): the database is the only
  holder of order state, so a queued mutable object would be a stale second copy. It keeps a set of
  waiting ids so an id already queued is not queued twice, and forgets an id the moment it is taken,
  so a legitimate re-enqueue (the barista's retry path) is never blocked.
- **`BaristaSupervisor` is a `SmartLifecycle`** (auto-start, phase above the executor's so it stops
  first) and `Barista` waits on a timed `poll` and re-checks its stop flag, so a context close *and*
  a Spring-7 test-context pause both stop the loops without a 30s executor-stop stall.
- **The barista thread is not in a transaction.** Every facade call it makes runs in its own
  transaction (see §9.5). A lost `@Version` race is retried (3 attempts, each reloading); an order
  that is gone or no longer preparable is skipped quietly; an unexpected failure is re-enqueued after
  a short delay, at most 3 attempts in total, then logged at ERROR and left for startup recovery.
- **Restart recovery** (`OrderRecovery`, on `ApplicationReadyEvent`): the queue is in memory, so on a
  real startup PLACED and PREPARING rows are re-enqueued oldest first. It is tied to
  `ApplicationReadyEvent`, not to the supervisor's restartable lifecycle, so a resumed test context
  does not recover twice. Recovery publishes no events. Correctness rests on idempotent consumption
  (preparing an order that is no longer PLACED/PREPARING is a quiet skip); the queue's dedupe is an
  optimisation. `PREPARING` is never committed by `PrepareOrderCommand` (one transaction: the recipe
  only logs steps), but a `PREPARING` row from any other source is finished, not stranded.
- **Notifications and the queue happen after commit**, the audit row inside the transaction (§9.5).

## 9. Chat & Persistence (Part 03)

### 9.1 Database

**PostgreSQL**, configured via Spring Data JPA (`application.properties` /
`application.yml`; a local Postgres instance or Docker Compose service for
development). This is a deliberate departure from the old project's SQLite —
Spring Data JPA's dialect support and the assignment's own JDBC framing are a
better fit for a real client-server database than continuing single-file
SQLite.

### 9.2 Entities

`CustomerEntity` and `UserEntity` were designed as one merged table, not two — a customer
placing an order and a barista fulfilling one are both just people with a role, and splitting
them would have meant `ChatSessionEntity.baristaId`/`customerId` pointing at two different
tables for what is structurally the same fact. Seven tables, seven entities:

| Entity (table) | Carries forward from | Notes |
|---|---|---|
| `UserEntity` (`user_accounts`) | `models.Customer` + *(new)* | The one identity table for every person — customer, barista, or manager — distinguished by `role` (`CUSTOMER`/`BARISTA`/`MANAGER`). `id`, `name`, `role`, `fulfilledOrders` (meaningful for `CUSTOMER` rows only; mutated exclusively via `UserRepository.incrementFulfilledOrders`, an atomic `UPDATE ... SET fulfilled_orders = fulfilled_orders + 1`, not read-modify-write — no `@Version` needed here). No separate `loyaltyTier` column; tier is derived at read time (`LoyaltyTier.forCount(...)`) so it can't drift from the count. Backs `ChatSessionEntity.customerId`/`baristaId` and `OrderEntity.customerId`, none of which had a real entity to reference before Part 03. |
| `OrderEntity` (`orders`) | `models.Order` | Persisted instead of held only in `CoffeeShop`'s in-memory list. `customer` (`@ManyToOne`, not a bare id), `baseCoffeeType`, `extras` (`@ElementCollection` + `@OrderColumn` — a `List`, not a `Set`: duplicate extras are meaningful and order must survive for Prototype reorder), `status`, `appliedLoyaltyTier` (frozen at placement, never recomputed against the customer's current tier), a four-column price breakdown (`price_base`/`price_extras`/`price_discount`/`price_total`, built directly from `PriceBreakdown` so its own invariant — `base + extras - discount = total` at scale 2 HALF_UP — can never violate the DB's `chk_order_price_consistent` CHECK), `placedAt`/`updatedAt`, and `@Version version` for optimistic locking (the only entity that needs it: async barista threads and REST requests can touch the same order row concurrently). |
| `order_extras` (no separate entity) | — | The backing table for `OrderEntity.extras`'s `@ElementCollection`; composite PK `(order_id, extra_index)`, `ON DELETE CASCADE`. |
| `PaymentEntity` (`payments`) | `adapter/` (Adapter pattern) | 1:1 with `OrderEntity` (`UNIQUE(order_id)`, owned by this entity — `OrderEntity` carries no inverse side). Gateway used, amount, status (`PENDING`/`PAID`/`FAILED`), optional `detail`. Inserted once, after the gateway responds, with a terminal status already known — never written `PENDING` first and updated later. |
| `OrderStatusHistoryEntity` (`order_status_history`) | — | The durable audit trail, written by one dedicated `OrderStatusChangedEvent` listener. `order` (FK), nullable `fromStatus` (null for an order's first, `PLACED` row), `toStatus`, `changedAt`, and a nullable `changedBy` (`UserEntity` FK) — null for an automated/system transition, set for a barista-attributed one. A plain FK can't check the referenced row's role, so BARISTA-only enforcement on `changedBy` is an application-layer rule (Part 03 Step 4), not a DB constraint. `OrderInvoker`'s in-memory command history stays a lightweight recent-activity/undo aid, never this. |
| `ChatSessionEntity` (`chat_sessions`) | `chat.ChatSession` (record) | `customer` (FK, not nullable), `barista` (FK, nullable until matched), `status` (`WAITING`/`ACTIVE`/`INACTIVE`), `createdAt`. A partial unique index (`customer_id` where `status <> 'INACTIVE'`) enforces at most one non-`INACTIVE` session per customer at the DB level, alongside the equivalent app-level check in `ChatService.startChat()`. |
| `ChatMessageEntity` (`chat_messages`) | `chat.ChatMessage` (record) | `session` (FK), `type` (`CHAT_MESSAGE`/`SYSTEM_MESSAGE`), nullable `sender` (FK — null for a `SYSTEM_MESSAGE`), `senderName` (kept as its own persisted column even though derivable from `sender`, since a later display-name change shouldn't rewrite what an old message shows), `content` (capped at 2000 chars via `chk_chat_message_content_length`, matched by a `@Size(max = 2000)` on the Part 03 Step 6 REST DTO), `sentAt`, nullable `order` (FK). |

Every FK above is a real `@ManyToOne`/`@OneToOne` association (`fetch = LAZY, optional = false`
where the relationship is mandatory), not a bare `Long` column. Indices beyond each table's PK:
`orders(customer_id)`, `orders(status)` (restart-recovery: re-finding in-flight orders),
`order_status_history(order_id, changed_at)`, `chat_sessions(customer_id)`,
`chat_sessions(status)`, `chat_messages(session_id, sent_at)` (ordered history load). Full
rationale for all of the above, including every constraint and index decision:
`CLAUDE.md` → "Schema additions needed before Part 03"; the full DDL lives in
`src/main/resources/db/migration/V1__init_schema.sql`, the schema's single source of truth
(`spring.jpa.hibernate.ddl-auto=validate`, not `update`).

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

### 9.5 Order persistence and lifecycle (Part 03 Step 3, as built)

**Domain vs entity.** `OrderEntity`/`UserEntity` are the only holders of order and customer state.
`model.Customer` and every in-memory holder (the maps and counters in `OrderService` /
`CustomerService`) are gone. `model.Order` is an **immutable snapshot record** built by `OrderMapper`
inside the read transaction (extras copied out while the session is open; the coffee description is
rebuilt via Factory + Decorator, never stored). Transition legality lives on
`OrderEntity.transitionTo`. `OrderEntity.customerId()` is a read-only mapped column so listing orders
does not initialise a lazy customer proxy per order.

**Transactions.** `OrderInvoker` runs every command in exactly one `TransactionTemplate` transaction
and records history/undo only *after* commit (a commit-time `@Version` failure is not recorded).
Commands hold order ids and load the managed entity inside `execute()`. `@Version` conflicts: the
REST layer maps `OptimisticLockingFailureException` to **409** (no auto-retry: the client should
re-read); the barista retries and skips as in §8.1. **Known limitation:** `placeOrder` derives the
customer's loyalty tier just before its command runs, so a fulfilment committing in that gap can make
the frozen tier stale by one (the tier and price stay consistent with each other).

**Events and phases (single publish point).** `OrderEventPublisher` is the only publisher, and it
throws if no transaction is active (a `@TransactionalEventListener` silently drops an event published
outside one). One event, two listener kinds:

| Listener | Phase | Why |
|---|---|---|
| `OrderStatusHistoryListener` | plain `@EventListener`, `MANDATORY` | the audit row commits or rolls back with the status change and cannot disagree with it; if the audit write fails, the status change rolls back |
| `OrderNotificationListener` | `AFTER_COMMIT` | never announce a change that rolled back; entry dropped when the order is FULFILLED/CANCELLED |
| `OrderQueueDispatcher` | `AFTER_COMMIT`, fresh placement only (`from == null`) | a barista can never dequeue an uncommitted order; an undo restoring PLACED cannot re-queue |

An `AFTER_COMMIT` listener must not touch the database (it would need its own `REQUIRES_NEW`).
`changed_by` is NULL in Step 3; `OrderCommand.actorUserId()` and the event's `actorUserId` are the
seam Step 4 fills (with the BARISTA-only check).

**Payment.** The gateway declining is a **FAILED `PaymentResult`, not an exception**: the command
commits a FAILED row (throwing would roll it back; a `REQUIRES_NEW` step needs a second pooled
connection per in-flight payment). The `payments` row is the order's *current* payment state
(`UNIQUE(order_id)`): a FAILED row is updated in place by a retry (only the latest failure is kept, no
attempt log), a PAID row is final. Only a READY order is payable. The amount is always the order's
persisted total (the adapter's reported amount is asserted equal). Concurrent payers are serialised by
a pessimistic row lock on the order taken **before** the gateway call, so the second sees PAID and is
rejected without being charged (409). `facade.processOrder` stops on a FAILED payment and returns an
`OrderOutcome`.

*The gateway call runs under that row lock, by design.* That is acceptable for the in-process simulated
gateways here and would need a redesign for a real network gateway (charge outside the lock, or an
idempotency key). A payer blocked on the lock holds a pooled connection, so many concurrent payers of
one order can consume the pool; the wait is therefore bounded by `coffeeshop.payment-lock-timeout-ms`
(default 5000; a PostgreSQL `lock_timeout` scoped to the transaction), after which the payer gets a
**409** (`OrderStateConflictException`) instead of waiting forever.

**Fulfilment.** Requires READY **and a PAID payment**, then increments `fulfilled_orders` with an atomic
SQL update in the same transaction, after the status change has been flushed (so the `@Version` check
fires first): exactly once per order. Unpaid fulfilment is 409 because loyalty tiers derive from
`fulfilled_orders`. **Known limitation:** cancelling an already-paid order has no refund flow (no real
processor, §4).

**Undo is a limited convenience, not a general reversal.** Only a placement that is still PLACED (it
cancels) and the cancellation of a READY order can be undone. Executing a command that cannot be undone
(payment, fulfilment, preparation) is a **barrier**: `OrderInvoker` empties its undo stack, so the
stack never jams behind an un-undoable command, `undoLast()` then returns nothing, and later
undoable commands work again; the stack is also capped. Undo of Pay, Fulfil and Prepare (and of
cancelling a PLACED order) throws `UndoNotSupportedException` (409): reversing them has effects outside
the order row (a payment, the loyalty count, the queue) that are not reversed.

**Reorder (Prototype)** rebuilds from the persisted base coffee and the ordered extras list (duplicates
and order preserved by `@OrderColumn`), never copies id/status/timestamps/price/tier, and ends in
`facade.placeOrder`, so the clone is priced at the customer's *current* tier and queued like any order.

### 9.6 Role enforcement (Part 03 Step 4, as built)

`Role` (`CUSTOMER` / `BARISTA` / `MANAGER`) has one real enforced boundary. It is a plain
service-layer check (`StaffAccess`); there is no Spring Security and no authentication (§4).

**Actor model.** Every facade method that changes order status takes an `Actor`: either a claimed user
id (`Actor.user(id)`) or `Actor.SYSTEM`, trusted in-process automation (the async barista loops,
`processOrder`, tests) that skips the role check and is never recorded as a person. The actor-less
signatures were removed rather than kept as overloads, so there is no unenforced back door. `Actor.SYSTEM`
is a public constant, so a guard test scans the `controller` package (source and bytecode) and fails
the build if any controller references it: an endpoint can never act as the system. A missing identity
must never silently become the system (`Actor.user(null)` is rejected).

**Who may do what.**

| Operation | Allowed | Otherwise |
|---|---|---|
| prepare, fulfil, cancel, undo (status transitions) | BARISTA, MANAGER, `SYSTEM` | CUSTOMER (even the order's own): **403** |
| pay | staff (BARISTA, MANAGER), the order's **own** CUSTOMER, `SYSTEM` | a different customer: **403**, before the gateway is called |
| place | CUSTOMER only (`CustomerNotFoundException` for any other user id) | 404 |
| reorder | see below | |

The check runs inside the command's own transaction, before anything changes, so it is atomic with the
change and a rejected action leaves no trace (proven for prepare, fulfil, cancel and pay: status, audit
rows, payments, `fulfilled_orders`, notifications and the gateway are untouched).

**`changed_by` rule.** Only a BARISTA is ever recorded; it is NULL for the system and for manager-driven
changes. `OrderStatusHistoryListener` (the one place it is written) verifies the role in the same
transaction and refuses anything else, rolling the change back, because a foreign key cannot check the
referenced user's role; its message names the user id and role. An undo is attributed to whoever asked for
it, not to whoever ran the original command.

**HTTP mapping.** No actor or an unknown user id: **401** (`UnknownActorException`, the caller cannot be
identified). A known user whose role does not permit the action: **403** (`RoleNotAllowedException`).
Unknown order: 404. Illegal transition, unpaid fulfilment, already-paid or not-READY payment, unsupported
undo, lost `@Version` race: 409. There is no REST endpoint for the transitions yet (Step 6); the mapping
is in place and tested for it. The caller's identity will arrive as a user id (an `X-User-Id` header),
not as credentials.

**Reorder.** The clone belongs to the *original order's customer*, and the request already names no
caller. This is safe until Step 6 because it exposes nothing that `POST /api/orders` does not: with no
authentication, anyone can already place an order for any customer id, and reorder cannot place one for
anybody except that order's own customer. A caller identity check for reorder (only that customer, or
staff) is a Step 6 concern, together with the endpoint's identity header.

**Creating staff.** `StaffService.createBarista` / `createManager` exist (used by tests and by Step 5
chat); there is deliberately no REST endpoint, because with no authentication an open endpoint that mints
managers would defeat the boundary.

**Known limitations and Step 6 notes.**
- Customers cannot cancel their own order through the API yet (only staff can).
- `undoLastAction` and cancel are staff-only; a customer cannot undo a placement.
- **Step 6 must solve bootstrapping:** a freshly started app has no way to create a barista or manager, so
  a reviewer could not exercise the role-gated endpoints. Plan: a manager-guarded staff-creation endpoint
  plus an initial manager seeded from configuration (property-driven, off by default in tests).
- A MANAGER's actions are not attributable in the audit trail (`changed_by` is NULL by rule).

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

## 11. Resolved decisions (previously open questions)

All four were open at scaffold time and are now settled — full rationale for each lives in
`CLAUDE.md` → "Resolving PRD §11's open questions"; this section just records the outcome so
the PRD and `CLAUDE.md` don't drift apart.

1. **Async dispatch shape.** N `@Async` consumer loops (pool size from a `ThreadPoolTaskExecutor`
   bean), launched once by `BaristaSupervisor` (a `SmartLifecycle` auto-start, so a paused-then-resumed
   context restarts them), waiting on a timed `orderQueue.poll(...)` so a stop flag is noticed
   without an interrupt — not a `@Scheduled` poller.
2. **Strategy bean keying.** Each `PricingStrategy` exposes `supportedTier()`; a
   `PricingStrategyResolver` builds an `EnumMap<LoyaltyTier, PricingStrategy>` from the injected
   `List<PricingStrategy>` — no bean-name string matching, no per-tier qualifier annotation.
3. **Auth scope.** `Role` gets one real enforced boundary: only `BARISTA`/`MANAGER` may call
   order-status-transition endpoints, only `BARISTA`-role users are eligible for `BaristaQueue`
   matching. Plain service-layer check, no Spring Security dependency.
4. **Project coordinates.** `dev.saberlabs.coffeechat`, groupId `dev.saberlabs`, artifactId
   `coffee-chat-application` — already in effect in `pom.xml`.

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
