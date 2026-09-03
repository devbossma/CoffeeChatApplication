# CLAUDE.md

Project-specific guidance for Claude Code sessions working in this repository.

## What this project is

**Not So Simple Chat** — the coffee shop chat application, reimplemented on Spring Boot.
This is a from-scratch Spring port of a plain-Java bootcamp project
(`../MyDesignPattern`, see below), not a copy of its source. Full scope, the
pattern-by-pattern design mapping, and what's explicitly out of scope live in
**`PRD.md`** at this repo's root — read that first for *what* to build. This file
is about *how* to work in this repo day to day.

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

Branch off `main` before committing (never commit straight to `main`); fast-forward
merge back once the branch is ready; delete the feature branch after merging. Push only
when explicitly asked to — a local commit is not the same as "done," but it's also not
an invitation to push on your own judgment.

## Working across the two repos

It's fine, and often necessary, for a session here to read files from
`../MyDesignPattern` by absolute or relative path for reference — that's what the table
above is for. It should not *write* to `../MyDesignPattern`; that project is finished
and its own history stands on its own. If a decision recorded there turns out to need
revisiting for the Spring version, record the new decision in this repo's `PRD.md`, not
by editing the old project.
