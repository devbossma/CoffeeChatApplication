# Not So Simple Chat

A coffee-shop ordering and chat application on Spring Boot 4 / Java 25 / PostgreSQL: a Spring port of a plain-Java
design-patterns bootcamp project. Scope and design are in [`PRD.md`](PRD.md); day-to-day working notes are in
[`CLAUDE.md`](CLAUDE.md).

## Run it

```bash
docker compose up -d                                   # local Postgres
mvn spring-boot:run -Dspring-boot.run.profiles=local   # the local profile seeds the first manager (id 1)
```

Then follow the curl walkthrough in [`docs/API.md`](docs/API.md): create a customer, have the seeded manager
create a barista, start a chat, place an order with `/order latte milk`, and watch it get prepared, paid and
fulfilled. `X-User-Id` is a demo identity claim, not authentication; that document says so plainly.

## Test it

```bash
mvn clean verify    # tests (Testcontainers starts its own Postgres) AND the 80% per-package coverage gate
```

Use `verify`, not `test`: only `verify` enforces the coverage gate. Docker must be running.

## Layout

| Package | What |
|---|---|
| `controller`, `web` | REST endpoints and the `X-User-Id` resolver |
| `facade` | `CoffeeShopFacade`, the only door into the order lifecycle |
| `chat` | chat matching, sessions and the `/order` message path |
| `command`, `observer`, `multithread` | order lifecycle commands, events, and the background baristas |
| `entity`, `repository`, `service` | persistence (Flyway-owned schema) and services |
| `factory`, `decorator`, `strategy`, `template`, `adapter`, `prototype`, `singleton` | the design patterns |
