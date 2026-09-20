# REST API

Everything below was exercised against a real running instance (local profile, Postgres from
`docker compose`); the sample responses are real output.

## Running it

```bash
docker compose up -d                                   # local Postgres
mvn spring-boot:run -Dspring-boot.run.profiles=local   # the "local" profile seeds the first manager
```

The local profile sets `coffeeshop.bootstrap.manager-name=Manager` (see
`src/main/resources/application-local.properties`). On an empty database the log then says:

```
Seeded initial manager 'Manager' with id 1: send it as the X-User-Id header
```

Without that property (the default, and in all tests) nothing is seeded. It is one property, and it is
idempotent: if any MANAGER already exists, nothing is created.

## Identity: `X-User-Id` is a demo claim, not authentication

Every endpoint except `POST /api/customers` and `GET /api/admin/shop` needs an `X-User-Id: <user id>` header.
**This is not authentication.** It is a claimed identity: user ids are small sequential numbers, so anyone can
send anyone's id. That is deliberate (PRD section 4: this project has no authentication system), and the
application still enforces *roles* on the claimed user (a customer really cannot fulfil an order). What
real authentication would replace: exactly one class, `web/ActorArgumentResolver`, which today reads the
header. A verified token or session would set the same `Actor` there and nothing downstream would change.

- A missing, blank, non-numeric or non-positive header, or an id that names no user, is `401` with
  `WWW-Authenticate: X-User-Id`.
- The trusted internal `SYSTEM` actor can never be reached over HTTP (a guard test enforces it).
- `404` is returned **before** `403`: an authenticated caller can probe whether an order id exists by comparing
  the two. Acceptable for a demo identity model.

## Walkthrough (curl)

Ids below are what an empty database gives you: manager 1, customer 2, barista 3.

```bash
B=http://localhost:8080; J='Content-Type: application/json'

# 1. a customer (no identity needed)
curl -s -X POST $B/api/customers -H "$J" -d '{"name":"Alice"}'
# {"id":2,"name":"Alice","fulfilledOrders":0,"loyaltyTier":"REGULAR"}

# 2. the seeded manager creates a barista
curl -s -X POST $B/api/staff -H "$J" -H 'X-User-Id: 1' -d '{"name":"Bob","role":"BARISTA"}'
# {"id":3,"name":"Bob","role":"BARISTA"}

# 3. the barista becomes available for chats
curl -s -X POST $B/api/chat/barista/ready -H 'X-User-Id: 3'          # 204

# 4. the customer starts a chat: matched at once because a barista is ready
curl -s -X POST $B/api/chat/sessions -H 'X-User-Id: 2'
# {"id":1,"status":"ACTIVE","customerId":2,"baristaId":3,"createdAt":"..."}

curl -s $B/api/chat/sessions/mine -H 'X-User-Id: 2'                   # with both names
# {"id":1,"status":"ACTIVE","customerId":2,"customerName":"Alice","baristaId":3,"baristaName":"Bob",...}

# 5. an order placed by chat: a message starting with /order goes through the same facade as POST /api/orders
curl -s -X POST $B/api/chat/sessions/1/messages -H "$J" -H 'X-User-Id: 2' -d '{"content":"/order latte milk"}'
# {"message":{...,"content":"/order latte milk"},
#  "reply":{...,"content":"Order #1 placed: Latte + Milk, total 4.50","orderId":1},"orderId":1}

# 6. the background barista threads prepare it within moments
curl -s $B/api/orders/1 -H 'X-User-Id: 2'
# {"id":1,"customerId":2,"coffee":"Latte + Milk",...,"priceTotal":4.50,"status":"READY",...}
#   (staff can also do it by hand: POST /api/orders/1/prepare with a staff X-User-Id)

# 7. pay (the customer, or staff at the counter)
curl -s -X POST $B/api/orders/1/pay -H "$J" -H 'X-User-Id: 2' -d '{"provider":"CASH"}'
# {"provider":"CASH","orderRef":"ORDER-1","amount":4.50,"status":"PAID","detail":"change $0.50"}

# 8. staff fulfil it (needs a PAID payment)
curl -s -X POST $B/api/orders/1/fulfil -H 'X-User-Id: 3'
# {"id":1,...,"status":"FULFILLED",...}

# 9. the history, oldest first, one page at a time
curl -s "$B/api/chat/sessions/1/messages?page=0&size=10" -H 'X-User-Id: 2'
# {"page":0,"size":10,"messages":[{"content":"Bob joined the chat",...},{"content":"/order latte milk",...},
#   {"content":"Order #1 placed: Latte + Milk, total 4.50","orderId":1,...}]}

# 10. end the chat
curl -s -X POST $B/api/chat/sessions/1/end -H 'X-User-Id: 2'          # {"ended":true}
```

## Endpoints

| Method | Path | Who | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/customers` | anyone | `201` | body `{name}` (1-255 chars) |
| `POST` | `/api/staff` | MANAGER | `201` | body `{name, role: BARISTA\|MANAGER}` |
| `GET` | `/api/admin/shop` | anyone | `200` | open flag and menu |
| `POST` | `/api/admin/shop/open`, `/close` | MANAGER | `200` | |
| `POST` | `/api/orders` | customer (for self) or staff | `201` | body `{customerId, type, extras?}` |
| `GET` | `/api/orders/{id}` | that order's customer, or staff | `200` | |
| `POST` | `/api/orders/{id}/reorder` | that order's customer, or staff | `201` | Prototype clone, placed as a new order |
| `POST` | `/api/orders/{id}/prepare`, `/fulfil`, `/cancel` | staff | `200` | returns the order |
| `POST` | `/api/orders/{id}/pay` | that order's customer, or staff | `200` paid | body `{provider: PAYPAL\|STRIPE\|CASH}` |
| `POST` | `/api/chat/sessions` | customer | `201` | `WAITING`, or `ACTIVE` if a barista was ready |
| `GET` | `/api/chat/sessions/mine` | customer, barista | `200` | own open session with the other side's name |
| `POST` | `/api/chat/barista/ready`, `/offline` | barista | `204` | |
| `POST` | `/api/chat/sessions/{id}/end` | its customer, its barista, or a manager | `200` | `{ended}`; idempotent |
| `POST` | `/api/chat/sessions/{id}/messages` | its customer or barista | `201` | body `{content}` (max 2000); only while `ACTIVE` |
| `GET` | `/api/chat/sessions/{id}/messages` | participants, managers | `200` | `?page=0&size=50` (size 1-200) |

**Deliberately not exposed: undo.** The facade has `undoLastAction`, but its stack is global across all users
and it can undo a placement, so over REST any staff member could cancel the last order placed by anyone. It
stays internal.

## Status codes

Every error body is a `ProblemDetail` (`application/problem+json`: `status`, `title`, `detail`, `instance`).

| Status | Meaning here |
|---|---|
| `400` | Malformed JSON, missing body, unknown enum value, wrong-typed path variable or query parameter, a failed validation (blank/oversized field, page or size out of range), an invalid chat message (blank, including only Unicode spaces, or over 2000 characters) |
| `401` | `X-User-Id` missing or not a positive number, or it names no user |
| `402` | Payment declined by the gateway; the body is the payment result (`status: FAILED`), the order stays `READY` and can be paid again |
| `403` | The user's role does not allow it, or it is another customer's order, or the user is not a participant of the chat |
| `404` | No such order, customer or chat session, or no open session for `/mine` |
| `409` | Shop closed; coffee off the menu; illegal order transition (for example preparing a READY order); not READY / already paid; fulfilling an unpaid order; a concurrent modification; an already-open chat (body has `existingSessionId`); posting to a chat that is not `ACTIVE` |
| `500` | An unforeseen error; the body is a generic message and the detail is only in the server log |

## Known limitations

- **No idempotency key.** Retrying an identical `/order` chat message, or `POST /api/orders`, places a second order.
- Customers cannot cancel their own order; only staff can. A manager's actions are not attributable in the audit trail.
- Stale chat sessions never expire: an abandoned session must be ended by its participants or a manager.
- Ready baristas are not remembered across a restart and must send `ready` again.
- Ids are sequential and guessable, and `X-User-Id` is not authentication (see above).
