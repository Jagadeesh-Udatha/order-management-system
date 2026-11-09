# Order Management System

A microservices backend for taking and fulfilling orders: products and stock, checkout, payment and customer
notifications. Nothing in it is tied to one industry. The same flow fits an electronics store, a pharmacy or
hospital supply desk, a grocery app or a B2B parts portal: only the product data changes.

Java 21, Spring Boot 3, Spring Cloud Gateway, Kafka, MySQL, MongoDB, Docker, Kubernetes (EKS manifests).

## Architecture

```mermaid
flowchart LR
    C[Client] --> G[api-gateway]
    G --> A[auth-service<br/>MySQL]
    G --> CAT[catalog-service<br/>MySQL]
    G --> O[order-service<br/>MySQL]
    G --> P[payment-service<br/>MySQL]
    G --> N[notification-service<br/>MongoDB]
    O -- price lookup (REST) --> CAT
    O <-- orders / inventory / payments topics --> K[(Kafka)]
    CAT <--> K
    P <--> K
    N <-- K
```

| Service | Owns | Talks to |
|---|---|---|
| api-gateway | routing, edge JWT check, X-Request-Id | all services over HTTP |
| auth-service | users, BCrypt passwords, issues HS256 JWTs (CUSTOMER / ADMIN) | nobody |
| catalog-service | products, search, available / reserved stock | Kafka |
| order-service | orders and the saga state | catalog (REST, prices), Kafka |
| payment-service | one payment per order, refunds | payment provider (simulated), Kafka |
| notification-service | message history in MongoDB, email sender | Kafka |

Each service has its own database schema and nobody reads another service's tables.

## How an order goes through

Choreography saga: every service reacts to the previous service's event, and order-service keeps the state.

```
POST /api/orders  ->  order PENDING  --OrderCreated-->  catalog reserves every line (all or nothing)
                                                        |-- StockReserved --> payment charges once
                                                        |                     |-- PaymentCompleted --> order CONFIRMED --> stock sold, email
                                                        |                     '-- PaymentFailed    --> order CANCELLED --> stock released, email
                                                        '-- StockRejected --> order CANCELLED, email
```

`POST /api/orders` answers `202 Accepted`. The client polls `GET /api/orders/{id}` until CONFIRMED or CANCELLED.

## Problems this handles, and how

- **Lost events.** Services never call Kafka inside a business transaction. They write the event to an
  `outbox_event` table in the same transaction, and a relay publishes it after commit. The relay reads with
  `FOR UPDATE SKIP LOCKED`, so two pods never send the same row.
- **Duplicate events.** Kafka delivery is at-least-once. Every consumer stores the event id in `processed_event`
  in the same transaction as its change and skips ids it has seen. Payment also has a unique `order_id`, so an
  order cannot be charged twice even with two different event ids.
- **Overselling.** Stock moves with one conditional `UPDATE ... WHERE available_qty >= ?`, never read-then-write.
  Lines are reserved in product-id order so two orders for the same items cannot deadlock.
  `StockServiceTest` sends 20 buyers at 5 units: exactly 5 succeed.
- **Double clicks on "Place order".** `Idempotency-Key` header, unique per customer. Same key and same body
  returns the first order (`Idempotent-Replay: true`). Same key with a different body is refused.
- **Events out of order.** Inventory and payment events are on different topics, so `PaymentCompleted` can be read
  before `StockReserved`. Order state changes only from allowed states; a late or repeated event changes nothing.
- **Stuck orders.** A scheduled job cancels orders still waiting after 15 minutes so stock is not held forever. If a
  payment then arrives for that cancelled order, order-service asks payment-service for a refund.
- **Price changes.** The price is read from catalog when the order is placed and copied onto the order line.

## Run it

Everything in Docker:

```bash
docker compose up --build
```

Then, through the gateway on port 8080:

```bash
# admin is created on first start (ADMIN_EMAIL / ADMIN_PASSWORD)
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@oms.local","password":"change-me-admin"}' | jq -r .accessToken)

curl -s localhost:8080/api/products -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"sku":"TV-55","name":"55 inch TV","category":"electronics","price":42999,"stock":10}'

curl -s localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"asha@example.com","fullName":"Asha","password":"secret-123"}'
```

Log in as the customer, then `POST /api/orders` with an `Idempotency-Key` header and
`{"lines":[{"productId":"...","quantity":1}]}`. Orders above Rs 1,00,000 are declined by the simulated provider,
which is an easy way to see the cancel and release path.

Tests (no Docker needed: H2 in MySQL mode, embedded Kafka, embedded MongoDB):

```bash
mvn verify
```

## Deploying

`k8s/` has the manifests I use on EKS: a Deployment and Service per service, readiness and liveness probes on the
actuator health groups, an HPA for order and catalog, and an ALB ingress in front of the gateway only.
MySQL, MongoDB and Kafka are expected in an `oms-infra` namespace (or RDS / MSK; only the URLs change).
CI runs `mvn verify` on every push and builds one image per service on main.

## Not done yet

- Real payment provider. `PaymentGateway` is the seam; the simulator approves up to a limit.
- Email sending only logs. The customer's address would come from auth-service.
- Gateway rate limiting (needs Redis) and distributed tracing.
- Dead-letter topic for events that keep failing; today the listener retries with the default back-off.
