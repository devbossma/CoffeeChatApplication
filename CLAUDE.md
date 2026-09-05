# CLAUDE.md

Project-specific guidance for Claude Code sessions working in this repository.

## What this project is

**Not So Simple Chat** — the coffee shop chat application, reimplemented on Spring Boot.
This is a from-scratch Spring port of a plain-Java bootcamp project
(`../MyDesignPattern`, see below), not a copy of its source. Full scope, the
pattern-by-pattern design mapping, and what's explicitly out of scope live in
**`PRD.md`** at this repo's root — read that first for *what* to build. This file
is about *how* to work in this repo day to day.

## Architecture decisions locked in before Part 01 starts

`MyDesignPattern` didn't have this section written down up front, and it cost a peer-review
round trip to catch that `ChatService` bypassed `CoffeeShopFacade`/the Command pipeline for
real orders, and that `processRequest` shipped as a permanent empty no-op. The decisions below
close the equivalent gaps in this project *before* any pattern gets implemented, not after.
Nothing here contradicts `PRD.md` §7–§11; it resolves §11's open questions and adds detail
§9.2's schema didn't have yet. If a future decision here needs to change, update this file and
`PRD.md` together — don't let them drift, the way the JaCoCo gate and this file's claim about it
briefly did.

### What `CoffeeShop` (the Singleton) actually owns

Spring already makes every `@Service`/`@Component` a singleton — the container does it for
free. So `CoffeeShop` copying its old job (holding the in-memory order list, id counters) would
be pure redundancy against `OrderRepository`, and a second, competing source of truth for order
state. That's the same class of bug as the `ChatService` bypass: two places can disagree about
what's true.

`CoffeeShop`'s real, non-redundant job is shop-wide operational state that is inherently
singular and does **not** belong on a per-order or per-customer row:

- `isOpen()` / `open()` / `close()` — whether new orders are currently accepted. Backed by an
  `AtomicBoolean` (read/written from concurrent request threads).
- The active menu — which `CoffeeType`s are being served right now.
- Barista pool size / shop-level config, read once at startup.

It has to be called by real code from day one, not left inert: `CoffeeShopFacade.placeOrder(...)`
calls `coffeeShop.isOpen()` first and rejects (409) if closed. `POST /api/admin/shop/open` and
`.../close` are the only two things that mutate it. No order data, no counters, no chat state
lives on this bean.

### `CoffeeShopFacade` is the *only* door into the order lifecycle

Hard rule: every order-lifecycle transition (place / prepare / pay / fulfill / cancel), from
every entry point, goes through `CoffeeShopFacade`. No controller, no `ChatService`, no async
Barista callback calls `OrderService`, `OrderInvoker`, or `CoffeeShop` directly for a lifecycle
transition. `CoffeeShopFacade` is the only class that builds `Command` objects and calls
`OrderInvoker.executeCommand(...)`.

Concretely:
- `OrderController.placeOrder(dto)` → `facade.placeOrder(order)`.
- `ChatService`, on detecting `/order ...` in a message → `facade.placeOrder(order)`. Never a
  direct repository/service call — this is exactly the shortcut `MyDesignPattern`'s `ChatService`
  took and had to be retrofitted after a peer review caught it.
- The async Barista consumer, on finishing prep → `facade.markPrepared(orderId)`, not a direct
  status update.
- `POST /api/orders/{id}/reorder` → `facade.reorder(orderId)`, which itself ends by calling
  `facade.placeOrder(...)` on the clone — reorder is not a second persistence path.

### One source of truth per fact (no redundant state)

| Fact | Owner | Anti-pattern this rules out |
|---|---|---|
| Order status | `OrderEntity.status` (DB) | An in-memory order list on `CoffeeShop`, or on any other bean |
| Order lifecycle audit trail | New `OrderStatusHistoryEntity`, written by the one `OrderStatusChangedEvent` listener | Treating `OrderInvoker`'s in-memory command history as durable — it stays a lightweight recent-activity/undo aid only |
| Customer loyalty tier | Derived at read time: `LoyaltyTier.forCount(customer.fulfilledOrders())` | Storing tier as its own mutable column that can drift from the count |
| Tier applied to a *specific* past order | `OrderEntity.appliedLoyaltyTier`, frozen at placement time | Recomputing an old order's discount against the customer's *current* tier |
| Live chat matching (who's waiting/free) | `BaristaQueue`, in-memory (correct per PRD §9.3 — it's coordination, not history) | Anything other than `BaristaQueue` writing `ChatSessionEntity.status` |
| Notifications | One publish point (the service/Command on a confirmed transition), one `@EventListener` | The async Barista callback *also* independently firing a notification for the same transition |

### Schema additions needed before Part 03 (on top of PRD §9.2)

- **No entity backs "barista" today** — `ChatSessionEntity.baristaId` references nothing. Add a
  `UserEntity` (id, name, `role`: CUSTOMER/BARISTA/MANAGER — single-table, `Role` as
  discriminator); `baristaId`/`customerId` become real foreign keys, not bare `Long`s.
- **No structured order line items** — an order needs its base coffee type, the applied extras
  (`order_extra(order_id, extra_type)`, or an `@ElementCollection`), and a price breakdown
  (base + extras − discount = total) — not one flattened description string. Reorder/Prototype
  needs this structure to reconstruct an equivalent order; a string can't be cloned meaningfully.
- **`OrderEntity.appliedLoyaltyTier`** — missing; see the source-of-truth table above.
- **No `PaymentEntity`** — the Adapter pattern simulates PayPal/Stripe/Cash but nothing persists
  which gateway was used, an amount, or a status (`PENDING`/`PAID`/`FAILED`). Add one, 1:1 with
  `OrderEntity`.
- **No `OrderStatusHistoryEntity`** — durable audit trail; see the source-of-truth table above.
- **Bare FK columns** — `ChatMessageEntity.sessionId` and `OrderEntity.customerId` should be real
  `@ManyToOne` associations, not `Long` columns, for referential integrity and JPQL joins.
- **Missing indices** — `chat_message(session_id, timestamp)` for the ordered history load;
  `order_entity(customer_id)` for per-customer queries (tier derivation, reorder lookup).

### Resolving PRD §11's open questions

1. **Async dispatch shape (§11.1) — resolved.** N `@Async` consumer loops (pool size from a
   `ThreadPoolTaskExecutor` bean), each started once via
   `@EventListener(ApplicationReadyEvent.class)`, blocking on `orderQueue.take()`. This is the
   closest Spring-idiomatic match to the old `Barista.run()` loop, and avoids the latency/busy-work
   a `@Scheduled` poller re-checking an empty queue would add.
2. **Strategy bean keying (§11.2) — resolved.** Each `PricingStrategy` exposes
   `LoyaltyTier supportedTier()`; a `PricingStrategyResolver` takes the injected
   `List<PricingStrategy>` and builds an `EnumMap<LoyaltyTier, PricingStrategy>` once at
   construction. No bean-name string matching, no custom qualifier per tier — avoids the exact
   fragile-string-routing mistake `framework/doc.md` in the reference project had to retrofit an
   enum to fix.
3. **Auth scope (§11.3) — resolved.** `Role` gets one real enforced boundary from the start:
   only `BARISTA`/`MANAGER` may call order-status-transition endpoints
   (`PATCH /api/orders/{id}/status`), and only `BARISTA`-role users are eligible for
   `BaristaQueue` matching. A plain service-layer check, no Spring Security — consistent with the
   "no full auth system" non-goal, but a real check, not a decorative field.
4. **Project coordinates (§11.4) — resolved, already in effect.** `dev.saberlabs.coffeechat`,
   groupId `dev.saberlabs`, artifactId `coffee-chat-application` — see `pom.xml`.

### Application operations flow

**A — REST order placement:**
`OrderController` → `facade.placeOrder(order)` → `coffeeShop.isOpen()` check → builds
`PlaceOrderCommand` → `OrderInvoker.executeCommand()` (`@Transactional`) → `OrderService`
persists `OrderEntity` (status `PLACED`, `appliedLoyaltyTier` frozen from the customer's
*current* derived tier) → publishes `OrderStatusChangedEvent(PLACED)` → the one listener
notifies + enqueues onto `OrderQueue` → `201` with the order id.

**B — async fulfillment:**
A Barista consumer loop (`@Async`, blocked on `queue.take()`) dequeues the order →
`facade.markPreparing(orderId)` → ... → `facade.markReady(orderId)` → event → notification.
Later, `POST /api/orders/{id}/pay` → `facade.pay(orderId, gateway)` → the Adapter → a
`PaymentEntity` row is written → `facade.markFulfilled(orderId)` →
`customer.fulfilledOrders()` increments (tier can only shift for the *next* order, never
retroactively).

**C — chat-driven order:**
`POST /chat/sessions/{id}/messages` → `ChatService` persists the `ChatMessageEntity` →
`OrderCommandParser` detects `/order ...` → `ChatService` calls `facade.placeOrder(...)` —
same as Flow A from that point on — → a confirmation message is persisted back into the
session.

**D — reorder (Prototype):**
`POST /api/orders/{id}/reorder` → `facade.reorder(orderId)` → fetch the original `OrderEntity`
→ obtain a prototype-scoped `OrderPrototype` bean via `ObjectProvider`, clone base coffee +
extras only (not id/status/timestamps) → `facade.placeOrder(clone)` — Flow A again, not a
second path.

## Before you write any test

Two things from the reference project carry over directly and matter before a single
`@Test` gets written:

1. **There is an 80% line-coverage gate, and it's enforced by `mvn verify`, not `mvn test`.**
   JaCoCo's `jacoco-check` execution fails the build if any package's line coverage drops
   below 80% (`PACKAGE` element, `LINE` counter, `COVEREDRATIO` minimum `0.80` — see this
   repo's `pom.xml`). `mvn test` only runs tests and writes the report; it does **not**
   enforce the gate. Always verify with `mvn clean verify` before considering work done,
   the same discipline `../MyDesignPattern` used throughout
   (`../MyDesignPattern/docs/IT-WORKS-ON-MY-MACHINE-README.md` is the full writeup).
   Exclude only what genuinely shouldn't count — entry points, wiring — never to hide a
   real gap.

2. **Every method under test needs both a success scenario and its real failure/edge-case
   scenarios, not just the happy path.** The floor is a floor, not a target to game — a
   test that only calls a method so the line lights up green doesn't count as covering it.
   Cover validation errors, nulls, not-found branches, and boundary conditions explicitly,
   the same way the reference project does. Concretely, that means:
   - One `@Nested` class per method under test, named `MethodNameTests`, with
     `@DisplayName("methodName()")`.
   - Inside it, one test per scenario — success path(s) *and* failure path(s) side by
     side — each with a descriptive method name and a `@DisplayName` that states the
     *behavior*, not the implementation (`"throws AuthException for wrong password"`, not
     `"testLogin2"`).
   - `@BeforeEach` resets any shared/mutable state so nothing leaks between tests. In this
     project that mostly means: a fresh `PostgreSQLContainer` per test class (Testcontainers'
     default) and resetting any `@Component`-scoped singleton state (the `CoffeeShop` bean)
     between tests the way `../MyDesignPattern` resets `CoffeeShop.getInstance()`.

   A concrete example to pattern-match against:
   `../MyDesignPattern/src/test/java/dev/saberlabs/auth/AuthServiceTest.java` — look at
   `LoginTests` and `RegisterTests` for the success/failure pairing in practice.

## Reference project: `../MyDesignPattern`

The plain-Java version of this exact application. Every functional decision already
argued through once (order lifecycle, loyalty tier thresholds, chat session matching,
the `/order` command marker, ...) is recorded there — don't re-derive it, look it up.
PRD.md section 6 lists the values that carry over directly.

### How to navigate it

Each GoF pattern package has its own `doc.md` explaining *why* it's built the way it
is, not just what the code does. Each bootcamp phase also has a root-level `docs/*.md`
write-up. Use whichever is more specific to what you're porting:

| Looking to port... | Read this in `../MyDesignPattern` first |
|---|---|
| Any of the 10 GoF patterns (overview) | `docs/DESIGN-PATTERNS-README.md` |
| Factory Method | `src/main/java/dev/saberlabs/factory/doc.md` |
| Observer | `src/main/java/dev/saberlabs/observer/doc.md` |
| Strategy | `src/main/java/dev/saberlabs/strategy/doc.md` |
| Decorator | `src/main/java/dev/saberlabs/decorator/doc.md` |
| Singleton | `src/main/java/dev/saberlabs/singleton/doc.md` |
| Command | `src/main/java/dev/saberlabs/command/doc.md` |
| Adapter | `src/main/java/dev/saberlabs/adapter/doc.md` |
| Facade | `src/main/java/dev/saberlabs/facade/doc.md` |
| Prototype | `src/main/java/dev/saberlabs/prototype/doc.md` |
| Template Method | `src/main/java/dev/saberlabs/template/doc.md` |
| Multithreading / producer-consumer | `docs/MULTITHREADING-README.md` + `src/main/java/dev/saberlabs/multithread/doc.md` |
| Chat feature + persistence | `docs/COFFEE-CHAT-README.md` |
| Testing discipline / JaCoCo gate | `docs/IT-WORKS-ON-MY-MACHINE-README.md` |
| Packaging (distributable jar) | `docs/PACKAGE-IT-README.md` — not part of this assignment's 4 parts, reference only |
| The reflection dispatch framework | `src/main/java/dev/saberlabs/framework/doc.md` — **out of scope here**, see PRD.md section 4 |

### Where the code actually lives

The domain code sits under `src/main/java/dev/saberlabs/`, one directory per pattern or
concern (mirrors the `doc.md` table above 1:1):

```
dev.saberlabs/
├── adapter/        payment gateway adapters (PayPal / Stripe / Cash)
├── auth/           User, Role, AuthService
├── chat/           ChatService, ChatSession, ChatMessage, BaristaQueue, repositories/
├── command/        order lifecycle commands + OrderInvoker
├── decorator/       Coffee extras (milk, sugar, whipped cream)
├── facade/          CoffeeShopFacade
├── factory/         per-coffee-type creators
├── framework/        reflection dispatch framework (NOT in scope for this project)
├── models/           Order, Customer, Coffee, OrderStatus, LoyaltyTier
├── multithread/       OrderQueue, Barista, CustomerThread
├── observer/          OrderNotificationService, OrderObserver
├── order/             OrderService
├── prototype/         CloneableOrder, CloneableCoffee
├── singleton/          CoffeeShop
├── strategy/            pricing strategies per loyalty tier
├── template/            per-coffee-type preparation steps
├── fx/ , views/         JavaFX UI (NOT in scope for this project -- REST only here)
└── CoffeeChatAppCLI.java, CoffeeShopApp.java, CoffeeChatAppFX.java  -- entry points
```

When porting a pattern, read its `doc.md`, skim the real class(es) it points to, then
implement the Spring-idiom version per PRD.md section 7.2 — don't transcribe the old
code, the point of the exercise is what changes under Spring (see PRD.md's
pattern-by-pattern mapping table for exactly what's supposed to change vs. stay the same
for each of the 10 patterns).

## This repo's stack

- Spring Boot 4.1.1, Java 25 (see `pom.xml` for the reasoning behind each pinned version)
- PostgreSQL via Spring Data JPA (not SQLite — a deliberate departure from the reference
  project, see PRD.md section 9.1)
- Local Postgres: `docker-compose.yml` at the repo root (`docker compose up -d`)
- Testing: JUnit 5 + Mockito (via `spring-boot-starter-test`), Testcontainers for
  `@DataJpaTest`/`@SpringBootTest` against a real Postgres, not an in-memory stand-in
- REST API only — no JavaFX/CLI UI, per PRD.md section 4

## Everyday commands

```bash
docker compose up -d          # start local Postgres (needed for `spring-boot:run` and
                               # anything hitting application.properties' datasource;
                               # Testcontainers-backed tests spin up their own container
                               # and don't need this)
mvn clean compile             # compile only
mvn clean test                # run tests, write the JaCoCo report, no gate enforced
mvn clean verify              # run tests AND enforce the 80% coverage gate -- use this
                               # before calling anything done
mvn spring-boot:run           # run the app locally against docker-compose's Postgres
```

## Git workflow

**One branch per assignment part, kept alive after merging.** This assignment is reviewed, and
the reviewer needs to see what happened in each of the 4 parts in isolation — not just the final
squashed state of `main`. Concretely:

- Branches: `part-01-design-patterns`, `part-02-multithreading`, `part-03-chat-jdbc`,
  `part-04-testing` — names match PRD.md §5's own part titles.
- **Sequential, not parallel.** Cut `part-02-multithreading` from `main` only after
  `part-01-design-patterns` is merged in, and so on — each part branch's diff against `main`
  should be exactly that part's work, since later parts build on earlier ones.
  `part-01-design-patterns` is the first one, cut from `main` as it stood after the
  architecture-decisions docs (this file, `PRD.md`) were merged.
- One PR per part when that part is done. Merge with a real merge commit (not squash, not
  fast-forward) so the branch's own commit history is preserved on `main` too.
- **Do not delete part branches after merging** — this is the one deviation from normal repo
  hygiene, made deliberately so a reviewer can check out `part-01-design-patterns` directly. Any
  other short-lived branch (a docs fix, a bug fix outside the 4 parts) still gets deleted after
  merging as usual.
- Never commit straight to `main`, even for docs. Push only when explicitly asked to — a local
  commit is not the same as "done," but it's also not an invitation to push on your own judgment.

## Bootcamp submission repo (separate, manual — Claude Code has no access)

The bootcamp grades work through its own git repo, reached only via their browser-based online
editor — it's not this local folder, has no remote wired up here, and Claude Code cannot push to
it or see its state. Its default branches are `dev` (day-to-day integration — all real commits
go here) and `master` (left alone unless the bootcamp says otherwise). Submission there is
manual: delete the old package, paste in the finished one, commit and push from that browser
terminal.

The same one-branch-per-part discipline as this repo applies there too, with `dev` standing in
for `main`. Since it's manual, it's run as a literal checklist in that terminal — branch
*before* pasting the new package (so the paste lands on the part branch, not `dev` by accident),
push with `-u` the first time, merge back with `--no-ff` (keeps a real merge commit instead of
flattening history), and never delete a part branch after merging (the reviewer needs to open it
directly). `master` is left alone unless the bootcamp says otherwise:

```bash
git checkout dev
git pull origin dev                      # skip the very first time — dev is already current
git checkout -b part-01-design-patterns  # branch BEFORE deleting/pasting the package

# ...delete the old package, paste the new one...

git add .
git commit -m "Part 01: design patterns"
git push -u origin part-01-design-patterns

# merge back — prefer the web editor's merge-request/PR button if it has one; otherwise:
git checkout dev
git merge --no-ff part-01-design-patterns
git push origin dev

# next part, once the previous one is merged:
git checkout dev
git pull origin dev
git checkout -b part-02-multithreading
```

## Working across the two repos

It's fine, and often necessary, for a session here to read files from
`../MyDesignPattern` by absolute or relative path for reference — that's what the table
above is for. It should not *write* to `../MyDesignPattern`; that project is finished
and its own history stands on its own. If a decision recorded there turns out to need
revisiting for the Spring version, record the new decision in this repo's `PRD.md`, not
by editing the old project.
