# Cocos Challenge API

REST API for instrument search, portfolios, and orders, built with Java 25, Spring Boot, Spring Data JPA, and PostgreSQL.

> **Stack:** Cocos requested Java for this challenge, so it uses Java and Spring Boot instead of the Node.js stack named in the base brief.

The provided schema and data are used as they are and never modified by the application.

## Requirements

- JDK 25 with `JAVA_HOME` set. `./mvnw --version` must report Java 25. Maven is provided by the wrapper (`mvnw.cmd` on Windows).
- The shared Neon database.
- Docker, only to run the container and the functional tests.

Shell commands use bash/zsh syntax; on Windows use WSL or Git Bash.

## Configuration

Copy the template and fill in the Neon database values:

```sh
cp .env.example .env
```

`.env` is ignored by Git and is the only configuration file: the application reads it when run locally and Docker Compose passes it to the container. It defines `DB_JDBC_URL` (without credentials), `DB_USERNAME`, and `DB_PASSWORD`, one `KEY=value` per line, without quotes. Optional: `SERVER_PORT` for a local run and `HOST_PORT` for the port Docker publishes (both default to 8080).

Swagger UI and the API docs are enabled only with the `local` profile. `./mvnw spring-boot:run` activates it; in the IDE or with the JAR, set the active profile to `local`. Docker uses the production configuration.

## Run

Locally:

```sh
./mvnw spring-boot:run
```

In IntelliJ, run `org.cocos.CocosChallengeApplication` with `local` as the active profile, or build the JAR with `./mvnw clean verify` and run `java -jar target/cocos-challenge-*.jar --spring.profiles.active=local`.

With Docker (no database container is started):

```sh
docker compose up --build
```

## Try it

```sh
curl 'http://localhost:8080/api/v1/instruments?q=macro'
curl http://localhost:8080/api/v1/users/1/portfolio
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
- Money uses `BigDecimal`; percentages are rounded `HALF_UP` to two decimals.
- New orders are timestamped in Argentina time (`cocos.orders.time-zone`), inferred from the provided orders, which fall within BYMA market hours.
- Search matches part of the ticker or name, ignoring case, and returns only stocks.
- The portfolio adds `dailyReturnPercentage`, which the brief explains but does not list; `GET /api/v1/users/{userId}/orders/{orderId}` is an extra endpoint.
- Creating or cancelling an order locks the user's row before reading the history, so two orders cannot spend the same cash or shares; if the lock wait expires, the API returns 409 `ACCOUNT_BUSY`.
- `src/test/resources/data/indexes.sql` adds indexes on `orders (userId, status, datetime, id)` for reading a user's history and on `marketdata (instrumentId, date DESC, id DESC)` for the latest quote. The tests apply it; to apply it to the database, run `psql "$DATABASE_URL" -f src/test/resources/data/indexes.sql`.
- `POST` orders is not idempotent: a retried request creates a new order. A future improvement is an `Idempotency-Key` header, which needs a new column outside the provided model.

## Tests

```sh
./mvnw verify
```

Functional tests, including order submission, run the full application against isolated PostgreSQL containers through Testcontainers and skip without Docker. No test touches the shared database.

## Author

Carolina Ponce, <carolina.ponce.ro@gmail.com>
