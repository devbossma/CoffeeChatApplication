# Welcome to Not So Simple Chat
***

## Task

This project ports a coffee shop ordering and chat app, originally built in plain Java as an
earlier step in this program, onto Spring Boot. The goal was never to just retype the same ten
design patterns in a new syntax. It was to find out which of them Spring already gives you for
free (a bean is a Singleton, dependency injection is half of what a Factory or Facade used to do
by hand), which ones need a real Spring-native replacement (hand-rolled threads and a custom
`Observable` become `@Async` and `ApplicationEventPublisher`), and which ones do not map onto
Spring cleanly at all, where you have to make an honest call about how far to force it.

The assignment splits into four parts, all delivered in this one repository:

- **Reimplement Design Patterns.** All ten GoF patterns from the original project, rebuilt the
  Spring way rather than copied over as-is. Singleton becomes a plain `@Component`, Observer
  becomes Spring's own event bus, Prototype finally gets to use Spring's real prototype scope
  instead of a hand-written clone method, and so on for the rest.
- **Multithreading with Spring.** Customers can place orders at the same time while baristas
  prepare them in the background, using `@Async` consumer loops on a configured thread pool
  instead of manually started `Thread`s, feeding off a thread-safe blocking queue.
- **Chat Functionality with Spring and JDBC.** Customers and baristas are matched and chat in
  real time. Orders can be placed through conversation with an explicit `/order` command, and
  everything, orders, payments, chat sessions, messages, is persisted through Spring Data JPA
  against PostgreSQL rather than hand-written JDBC against SQLite.
- **Testing with Spring Boot and JUnit.** JUnit 5 and Mockito unit tests, integration tests
  against a real Testcontainers PostgreSQL instance, and an 80 percent per-package coverage
  floor that the build itself enforces, so the test suite cannot quietly rot over time.

## Description

### The pieces

| Package | What it does |
|---|---|
| `controller`, `web` | REST endpoints and the `X-User-Id` identity resolver |
| `facade` | `CoffeeShopFacade`, the single door into the order lifecycle (place, prepare, pay, fulfil, cancel), used by every entry point into the app |
| `chat` | matching customers with baristas, chat sessions, and the `/order` message path |
| `command`, `observer`, `multithread` | order lifecycle commands (with undo and history), the `OrderStatusChangedEvent` and its listeners, and the background barista threads |
| `entity`, `repository`, `service` | persistence. The schema lives in versioned Flyway migrations, not in a generated `ddl-auto` schema |
| `factory`, `decorator`, `strategy`, `template`, `adapter`, `prototype`, `singleton` | the ten design patterns, each one a real, working piece of the running app rather than a textbook example sitting off to the side |

### How an order flows through the app

Here is what actually happens when a customer sends `/order latte milk` in chat. A direct
`POST /api/orders` call takes almost the same path once the order itself has been parsed:

1. The chat service saves the customer's message first, in its own database transaction, so the
   message survives no matter what happens to the order next, even if the order ends up being
   rejected a moment later.
2. The text is checked for the `/order` marker. An exact `/order` prefix is required on purpose:
   a bare word like "order" would false-positive on an ordinary sentence such as "order latte
   from this place was amazing", but nothing in normal chat ever writes a sentence that starts
   with a slash.
3. `CoffeeShopFacade.placeOrder(...)` is the only way an order ever gets placed. Not a
   repository call, not a direct call into the order service, only the facade. It checks that
   the shop is open, works out the customer's current loyalty tier, builds the order through the
   Factory, Decorator and Strategy patterns, and runs the whole thing as one `Command` inside one
   transaction.
4. Once that transaction commits, an `OrderStatusChangedEvent` fires. One listener writes the
   permanent audit row in the very same transaction as the status change. Separate listeners,
   triggered only after commit, send the notification and put the order onto the barista queue.
   Never before the row is actually committed, or a barista could end up pulling an order off the
   queue that does not exist yet as far as the database is concerned.
5. A background `@Async` barista picks the order up, prepares it, and moves its status to ready,
   again only through the facade, never by writing to the order row directly.
6. The chat service writes a confirmation message back into the session, carrying the real order
   id once placement has succeeded.

### Design notes

A few decisions are worth knowing about up front, because they shape almost everything else in
the codebase.

- **The facade is the only door.** Every single order lifecycle transition, whether it comes
  from a REST controller, from chat, or from the background barista, goes through
  `CoffeeShopFacade`. This matters because the earlier plain Java version of this project did not
  hold that rule consistently: its chat layer once bypassed its own facade for real orders, and
  it took a peer review to catch it. Here, the rule was decided before a single pattern was
  implemented, not patched in afterward.
- **One fact, one owner.** Order status lives only on the order's own database row. A customer's
  loyalty tier is derived from how many orders they have had fulfilled; it is never stored as its
  own column that could quietly drift out of sync with the count behind it.
- **`X-User-Id` is a demo identity claim, not real authentication.** There is no login system
  here. A caller just claims a user id in a header, and the app enforces roles on whoever that id
  actually names (a customer genuinely cannot fulfil their own order, no matter what header they
  send). This is explained in detail in [`docs/API.md`](docs/API.md), and it is a deliberate
  scope decision for this assignment rather than an oversight.
- **A handful of known limitations exist on purpose**, and they are worth knowing before you go
  looking for them as bugs:
  - There is no idempotency key on placing an order, so retrying the exact same request can
    place a second order.
  - Cancelling an order that has already been paid for does not trigger a refund.
  - A chat session left waiting for a barista never expires on its own.
  - A barista's "ready" status is not remembered across an application restart. Orders and chat
    sessions already in the database are recovered automatically; barista availability is not.
  - A manager's own actions are not individually attributed in the order history, only a
    barista's are.

## Installation

You will need **JDK 25 or newer**, **Maven**, and **Docker**. PostgreSQL always runs inside a
container; there is no in-memory substitute standing in for it anywhere, by design, since this
project is tested against the exact same kind of database it runs against in real use.

```bash
git clone https://github.com/devbossma/CoffeeChatApplication.git
cd CoffeeChatApplication
mvn clean install
```

## Usage

### Running the app

```bash
docker compose up -d
```

Then, on Windows in PowerShell, the `-D` property has to be quoted as a single argument, or
PowerShell will split it apart at the `=` sign and Spring Boot will never actually see it:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

On macOS and Linux the unquoted form works too, but quoting it does no harm there either, so the
one command above works everywhere without having to remember which platform you are on.

The `local` profile seeds one manager account on an empty database. Watch the startup log for a
line like this:

```
Seeded initial manager 'Manager' with id 1: send it as the X-User-Id header
```

That id is what you send back as the `X-User-Id` header to do anything that only a manager can
do, such as creating a barista account.

### Trying the API

Follow the curl walkthrough in [`docs/API.md`](docs/API.md) step by step. It was run against a
real instance of this app, and the sample responses shown there are the real output it produced,
not invented examples.

### Issues you might run into while testing

A few things have tripped people up while running this manually, worth knowing in advance:

- The walkthrough's sample ids (manager 1, customer 2, barista 3, and so on) only match a
  completely empty database. If you have already run it once, the next run will hand out
  different ids, and the responses you get back will not line up with what the document shows.
  Reset the local database first with `docker compose down -v`, then `docker compose up -d`. The
  `-v` flag removes the database's data volume along with the containers, so it throws away
  everything stored in it, not just the chat sessions and orders you created while testing.
- Order preparation happens in the background on its own thread, not instantly. If you place an
  order and immediately check its status, you may briefly see it still marked as placed before
  it flips over to ready a moment later. That delay is expected, not a bug.
- A barista who was marked "ready" before you stopped the app will not still be marked ready
  after you start it again. They need to call the ready endpoint again. Orders and chat sessions
  that already existed in the database come back on their own; barista availability does not.
- If Docker Desktop itself gets restarted, the local database container usually needs a manual
  restart too (`docker compose up -d` again) before `mvn spring-boot:run` can connect to it.
- The very first time Testcontainers or docker compose needs the PostgreSQL image, it has to
  download it, which can take a few minutes depending on your connection. Every run after that is
  fast, since the image is already on disk.

### Running the tests

```bash
mvn clean verify
```

This needs Docker running, because the automated tests start their own disposable PostgreSQL
container through Testcontainers rather than using any in-memory stand-in. The build does two
things at once: it runs the entire test suite, and it enforces an 80 percent minimum line
coverage floor for every package, so a shrinking test suite fails the build loudly instead of
slipping through quietly. The suite currently stands at 856 tests, all of them passing, taking
about 1 minute and 22 seconds in total.

Use `verify`, not `test`, when you actually want to know whether the build is sound. `mvn test`
only runs the suite and writes a coverage report to disk; it does not fail the build if coverage
has dropped below the floor.

### The Core Team


<span><i>Made at <a href='https://qwasar.io'>Qwasar SV -- Software Engineering School</a></i></span>
<span><img alt='Qwasar SV -- Software Engineering School's Logo' src='https://storage.googleapis.com/qwasar-public/qwasar-logo_50x50.png' width='20px' /></span>
