# Cocos Challenge API

REST API for instrument search, portfolios, and orders, built with Java 25, Spring Boot, Spring Data JPA, and PostgreSQL. Cocos requested Java for this challenge, so it uses Java instead of the Node.js stack named in the base brief.

The provided schema and data are used as they are.

## Requirements

- JDK 25 (`./mvnw --version` must report Java 25). Maven is provided by the wrapper (`mvnw.cmd` on Windows).
- Docker, for the local database and the functional tests.

Shell commands use bash/zsh syntax; on Windows use WSL or Git Bash.

## Run

**Local database.** Start PostgreSQL with the provided schema, data, and indexes, then the API with the `local` profile:

```sh
docker compose up -d --wait db
./mvnw spring-boot:run
```

The database, user, and password are `cocos`. On its first start the container loads `src/test/resources/data/database.sql` and `indexes.sql`; `docker compose --profile local-db down` removes it, so the next start loads the data again. If port 5432 is taken, set `DB_PORT` for both commands; for a port other than 8080, set `SERVER_PORT`.

In IntelliJ, run `org.cocos.CocosChallengeApplication` with `local` as the active profile.

**Neon.** To run locally against the shared database, also set `DB_JDBC_URL` (without credentials), `DB_USERNAME`, and `DB_PASSWORD`; they take precedence over the local values.

**Docker.** Docker runs the API only against Neon, with the credentials in `.env` (ignored by Git, one `KEY=value` per line, without quotes). Set `HOST_PORT` there to publish a port other than 8080.

```sh
cp .env.example .env
docker compose up --build
```

## Try it

- **Swagger UI** (`local` profile only): <http://localhost:8080/swagger-ui.html>, with every endpoint, parameter, and error code.
- **Postman:** import `postman/cocos-challenge.openapi.yaml`, with examples for each kind of order.
- **Health:** `/actuator/health/liveness`, and `/actuator/health/readiness`, which also checks the database.

```sh
curl 'http://localhost:8080/api/v1/instruments?q=macro'
curl http://localhost:8080/api/v1/users/1/portfolio
curl -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId":47,"side":"BUY","type":"MARKET","amount":10000}'
```

Errors share one JSON envelope with a `code`, a message, and per-field details.

## Assumptions

- The brief's `size` is exposed as `quantity` and stored in `orders.size`. The brief lists `marketdata.datetime`; the provided data has `marketdata.date`, which is what is read.
- Search matches part of the ticker or name, ignoring case, and returns only stocks. The portfolio adds `dailyReturnPercentage`, and `GET /api/v1/users/{userId}/orders/{orderId}` is an extra endpoint.
- The API creates only `BUY` and `SELL` orders; `CASH_IN` and `CASH_OUT` are read from the history.
- `MARKET` orders use the latest `close`, which in the provided data is from 2023, and ignore a submitted `price`. `LIMIT` orders stay `NEW` until cancelled.
- With `amount`, the order trades as many whole shares as the amount covers. Prices and amounts allow two decimals.
- A `NEW` `CASH_IN` adds no cash until it is `FILLED`; a `NEW` `CASH_OUT` reserves the cash.
- User 1 holds -10 shares of BMA (a filled purchase of 20 and a filled sale of 30). This is treated as an inconsistency in the history, not corrected: the portfolio shows it with a null return and a null account total, and new orders for that instrument fail with 409 `INCONSISTENT_POSITION`. Cancelling still works, and the rule applies to any user and instrument.

## Decisions

- Code is organized by feature (`instruments`, `marketdata`, `orders`, `portfolio`, `user`). Each one keeps its controllers, DTOs, and repositories in `infrastructure`, its services in `application`, and its entities in `domain`; services work with the API DTOs directly to keep the code small.
- Cash and positions are recalculated from the user's `FILLED` orders on each request, as the brief asks, so there is no balance to keep in sync. `NEW` orders reserve cash or shares until cancelled.
- An order without enough cash or shares is saved as `REJECTED` and returns 201, so the attempt stays in the history.
- `availableCash` excludes reserved cash; `totalAccountValue` includes it, since it still belongs to the user. Money uses `BigDecimal` with two decimals; percentages are rounded `HALF_UP`.
- Creating or cancelling an order locks the user's row first, so two concurrent orders cannot spend the same cash or shares. If the lock wait expires, the API returns 409 `ACCOUNT_BUSY`.
- New orders are timestamped in Argentina time, inferred from the provided orders, which fall within market hours.
- `indexes.sql` adds indexes for reading a user's history and the latest quote. The tests apply it; to apply it to Neon, run `psql "postgresql://USER:PASSWORD@HOST/neondb?sslmode=require" -f src/test/resources/data/indexes.sql`.
- `POST` orders is not idempotent: a retried request creates a new order. An `Idempotency-Key` header would fix it, but needs a column outside the provided model.

## Tests

```sh
./mvnw verify
```

Functional tests run the full application against PostgreSQL containers through Testcontainers and never touch Neon. Without Docker they are skipped and `verify` still passes, so check that no tests are skipped. GitHub Actions runs them on pushes to `main` and on pull requests.

## Author

Carolina Ponce, <carolina.ponce.ro@gmail.com>
