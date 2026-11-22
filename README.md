# Cocos Challenge API

REST API for instrument search, portfolios, and orders, built with Java 25, Spring Boot, Spring Data JPA, and PostgreSQL.

> **Stack:** Cocos requested Java for this challenge, so it uses Java and Spring Boot instead of the Node.js stack named in the base brief.

The provided schema and data are used as they are and never modified by the application.

## Requirements

- JDK 25 with `JAVA_HOME` set. `./mvnw --version` must report Java 25. Maven is provided by the wrapper (`mvnw.cmd` on Windows).
- Docker, for the local database, the container, and the functional tests.
- Optional: the shared Neon database.

Shell commands use bash/zsh syntax; on Windows use WSL or Git Bash.

## Configuration

**Local database.** The `local` profile connects to PostgreSQL on `localhost:5432`, with database, user, and password `cocos`. Start it with the provided schema, data, and indexes:

```sh
docker compose up -d db
```

On its first start the container loads `src/test/resources/data/database.sql` and then `indexes.sql`. `docker compose down` removes it, so the next start loads the data again from scratch.

**Neon.** To run locally against the shared database, set `DB_JDBC_URL` (without credentials), `DB_USERNAME`, and `DB_PASSWORD` as environment variables; they take precedence over the local values. In IntelliJ, add them to the run configuration.

**Docker.** The API container connects to Neon with the values in `.env`, which is ignored by Git:

```sh
cp .env.example .env
```

One `KEY=value` per line, without quotes. Optional: `HOST_PORT` for the port Docker publishes and `SERVER_PORT` for a local run (both default to 8080).

Swagger UI and the API docs are enabled only with the `local` profile. `./mvnw spring-boot:run` activates it; in the IDE or with the JAR, set the active profile to `local`. Docker uses the production configuration.

## Run

Locally:

```sh
./mvnw spring-boot:run
```

In IntelliJ, run `org.cocos.CocosChallengeApplication` with `local` as the active profile, or build the JAR with `./mvnw clean verify` and run `java -jar target/cocos-challenge-*.jar --spring.profiles.active=local`.

With Docker, against Neon (the local database is not started):

```sh
docker compose up --build
```

## Try it

```sh
curl 'http://localhost:8080/api/v1/instruments?q=macro'
curl http://localhost:8080/api/v1/users/1/portfolio
```

Orders (these write to the configured database):

```sh
# MARKET buy by amount: buys as many whole shares as the amount covers, FILLED
curl -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId":47,"side":"BUY","type":"MARKET","amount":10000}'

# LIMIT sell: stays NEW and reserves the shares until cancelled
curl -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId":47,"side":"SELL","type":"LIMIT","quantity":5,"price":950}'

# Selling more shares than held: saved as REJECTED, still 201
curl -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId":54,"side":"SELL","type":"MARKET","quantity":1000}'

# Cancel a NEW order (use the id returned by the LIMIT order)
curl -X POST http://localhost:8080/api/v1/users/1/orders/{orderId}/cancel
```

- **Swagger UI** (local profile only): <http://localhost:8080/swagger-ui.html>.
- **Postman:** import `postman/cocos-challenge.openapi.yaml`. It creates one request per endpoint with examples, including each kind of order.
- **Health:** `/actuator/health/liveness` does not query the database; `/actuator/health/readiness` does, and wakes a suspended Neon.

The endpoints, their parameters, and the error codes are documented in Swagger UI, which is generated from the code, and in the Postman file. Errors share one JSON envelope with a code and per-field details.

## Assumptions

**Brief**

- The base brief names Node.js; Cocos requested Java, so this uses Java and Spring Boot.
- The brief's `size` is exposed as `quantity` in the API and stored in `orders.size`.
- The brief lists `marketdata.datetime`; the provided SQL and the shared database have `marketdata.date`, which is what is read.
- Tables and data are unchanged. Two optional indexes are proposed in `src/test/resources/data/indexes.sql` (see Decisions).

**BMA inconsistency**

- User 1 holds -10 shares of BMA (instrument 31): a filled purchase of 20 and a filled sale of 30. This is treated as an inconsistency in the provided history, not corrected.
- The portfolio still returns 200 with the historical quantity, a null return for that position, and a null account total.
- New buys and sells of that instrument by that user fail with 409 `INCONSISTENT_POSITION`. Cancelling existing `NEW` orders still works. The rule applies to any user and instrument with inconsistent history.

## Decisions

- Code is organized by feature (`instruments`, `marketdata`, `orders`, `portfolio`, `user`), each with domain, application, and infrastructure layers; controllers use explicit `Request` and `Response` DTOs.
- Cash and positions are calculated from the user's `FILLED` and `NEW` orders, as the brief asks.
- An order without enough cash or shares is saved as `REJECTED` and returns 201; `NEW` `LIMIT` orders reserve cash or shares until cancelled.
- `availableCash` excludes the cash reserved by `NEW` buy orders; `totalAccountValue` includes it, since it still belongs to the user, and adds the market value of the positions.
- Money uses `BigDecimal`; percentages are rounded `HALF_UP` to two decimals.
- New orders are timestamped in Argentina time (`cocos.orders.time-zone`), inferred from the provided orders, which fall within BYMA market hours.
- Search matches part of the ticker or name, ignoring case, and returns only stocks.
- The portfolio adds `dailyReturnPercentage`, which the brief explains but does not list; `GET /api/v1/users/{userId}/orders/{orderId}` is an extra endpoint.
- Creating or cancelling an order locks the user's row before reading the history, so two orders cannot spend the same cash or shares; if the lock wait expires, the API returns 409 `ACCOUNT_BUSY`.
- `src/test/resources/data/indexes.sql` adds indexes on `orders (userId, status, datetime, id)` for reading a user's history and on `marketdata (instrumentId, date DESC, id DESC)` for the latest quote. The tests apply it; to apply it to the database, run `psql "postgresql://USER:PASSWORD@HOST/neondb?sslmode=require" -f src/test/resources/data/indexes.sql` with the values from `.env`.
- `POST` orders is not idempotent: a retried request creates a new order. A future improvement is an `Idempotency-Key` header, which needs a new column outside the provided model.

## Tests

```sh
./mvnw verify
```

Functional tests, including order submission and the portfolio of the provided data, run the full application against isolated PostgreSQL containers through Testcontainers. No test touches the shared database.

They need Docker running: without it they are skipped and `verify` still passes, so check that the summary shows no skipped tests. The GitHub Actions workflow runs them on pushes to `main` and on pull requests.

## Author

Carolina Ponce, <carolina.ponce.ro@gmail.com>
