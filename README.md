# go-ledger-java

## What's in this?

- **Domain model & posting engine** — `Tenant`, `Account`, `Transaction`,
  `Posting`, with the zero-sum invariant enforced in `LedgerService` *and*
  as a Postgres trigger backstop (`V1__init.sql`).
- **REST API** (Spring MVC) — tenants, API keys, accounts, transactions,
  reversal, account statements/balances, trial balance.
- **Postgres persistence** via Spring Data JPA + Flyway migrations.
- **API-key auth** — `Authorization: Bearer <key>`, keys stored only as a
  SHA-256 hash, scopes (`read`, `post`, `approve`, `admin`).
- **Mandatory idempotency keys** on posting endpoints, with a DB-level
  unique constraint as the source of truth under concurrent retries.
- **First-run bootstrap** — on first boot with no tenants, creates a
  `default` tenant and prints a one-time admin API key to the logs.
- Docker Compose (Postgres + app) and a multi-stage Dockerfile.



## Run it

### With Docker Compose (recommended)

```
docker compose up --build
```

Watch the logs for the one-time bootstrap admin key:

```
app-1  | Bootstrap admin API key (save this now, it will not be shown again):
app-1  |   glk_...
```

Then:

```
curl localhost:8080/healthz
```

### From source

Requires JDK 21+, Maven, and a reachable Postgres.

```
cp .env.example .env   # edit if your Postgres isn't the default
export $(cat .env | xargs)
mvn spring-boot:run
```

## A short tour 
```
ADMIN_KEY=glk_...   # from the bootstrap log line

# 1. Create a tenant
curl -X POST localhost:8080/v1/admin/tenants \
  -H "Authorization: Bearer $ADMIN_KEY" -H "Content-Type: application/json" \
  -d '{"name": "acme-corp"}'

# 2. Issue a key for it
curl -X POST localhost:8080/v1/admin/keys \
  -H "Authorization: Bearer $ADMIN_KEY" -H "Content-Type: application/json" \
  -d '{"tenantId": "<tenant-id>", "name": "acme-api", "scopes": ["read", "post"]}'
KEY=glk_...   # from the response, shown once

# 3. Create two accounts
curl -X POST localhost:8080/v1/accounts \
  -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -d '{"name": "Checking", "type": "asset", "currency": "USD"}'
curl -X POST localhost:8080/v1/accounts \
  -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -d '{"name": "Income", "type": "income", "currency": "USD"}'

# 4. Post a balanced transaction ($10 deposit)
curl -X POST localhost:8080/v1/transactions \
  -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "currency": "USD",
    "postings": [
      {"accountId": "<checking-id>", "amount": 1000, "description": "deposit"},
      {"accountId": "<income-id>", "amount": -1000, "description": "deposit"}
    ]
  }'

# 5. Read the balance
curl localhost:8080/v1/accounts/<checking-id>/balance -H "Authorization: Bearer $KEY"

# 6. View the trial balance (should net to zero per currency)
curl localhost:8080/v1/reports/trial-balance -H "Authorization: Bearer $KEY"

# 7. Reverse a transaction
curl -X POST localhost:8080/v1/transactions/<transaction-id>/reverse \
  -H "Authorization: Bearer $KEY" -H "Idempotency-Key: $(uuidgen)"
```

## Project structure

```
go-ledger-java/
├── src/main/java/com/goledger/
│   ├── domain/       # JPA entities: Tenant, Account, Transaction, Posting, ApiKey, IdempotencyRecord
│   ├── repository/   # Spring Data JPA repositories
│   ├── service/       # LedgerService (posting engine), AdminService, ReportService
│   ├── security/     # API-key hashing/auth filter, AuthContext (tenant + scopes per request)
│   ├── web/          # REST controllers + dto/
│   ├── exception/    # Domain exceptions + @RestControllerAdvice
│   └── config/       # BootstrapRunner (first-run admin key)
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/V1__init.sql   # schema + zero-sum constraint trigger
├── src/test/java/com/goledger/
│   ├── service/      # LedgerServiceValidationTest (unit), AdminServiceTest (unit),
│   │                 # LedgerServiceIntegrationTest (real Postgres via Testcontainers)
│   ├── security/     # ApiKeyHasherTest (unit)
│   ├── web/          # ApiFlowIntegrationTest (MockMvc, real Postgres, full HTTP flow)
│   └── support/      # AbstractIntegrationTest (shared Testcontainers base)
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

## Running the tests

```bash
mvn test
```

`LedgerServiceIntegrationTest` and `ApiFlowIntegrationTest` spin up a real
Postgres via Testcontainers (a singleton container shared across both
classes), so **a Docker daemon must be available to the build** — this is not
optional for a ledger, because the zero-sum invariant and idempotency-race
handling depend on real Postgres semantics (constraint triggers, unique
constraints, SERIALIZABLE conflict behaviour) that an in-memory database like
H2 does not faithfully reproduce. `LedgerServiceValidationTest`,
`AdminServiceTest`, and `ApiKeyHasherTest` are plain unit tests and don't need
Docker.

The most important test in the suite is
`concurrentPostsWithTheSameIdempotencyKeyProduceExactlyOneTransaction` — it
fires the same idempotency key from 8 threads at once and asserts exactly one
transaction results. Writing that test is what surfaced a real bug (see
below), which is exactly the point of having it.

### Two correctness issues this test suite found and fixed

Writing these tests, rather than just reviewing the code by eye, caught two
real problems in the initial port:

1. **The zero-sum trigger could misfire under batching.** It was originally a
   `FOR EACH STATEMENT` trigger reading a transition table
   (`REFERENCING NEW TABLE`). That only sees the rows written by a *single*
   SQL statement execution — but Hibernate doesn't guarantee both postings in
   a transaction go out as one multi-row `INSERT`; depending on JDBC batching
   settings, each can be its own statement. An immediate check could then see
   only the first leg of a valid, balanced transaction and reject it. Fixed
   by making it a `DEFERRABLE INITIALLY DEFERRED` constraint trigger that
   fires at commit time, by which point every row is guaranteed to already be
   in the table regardless of how the inserts were batched. See the comments
   in `V1__init.sql`.
2. **Idempotency races under `SERIALIZABLE` weren't fully handled.**
   `postTransaction`/`reverseTransaction` run at `SERIALIZABLE` isolation, and
   the check-then-insert pattern they use for idempotency is exactly the
   textbook case Postgres flags as a `40001` serialization failure — which
   Spring surfaces as `CannotSerializeTransactionException`, a *sibling* of
   `DataIntegrityViolationException`, not a subtype. The original `catch`
   block only caught the latter, so a legitimately duplicate concurrent
   request could throw a raw, ungraceful exception instead of replaying the
   winner's result. Fixed by also catching `ConcurrencyFailureException` (the
   common parent) in both methods. See `LedgerService.java`.

