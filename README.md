# Salem Todo DevOps

A Spring Boot REST API for organizing projects and tasks, with account
authentication, project labels, task filtering, and pagination.

Built with Java 17, Spring Security, Spring Data JPA, PostgreSQL, and Flyway.
The repository includes a Maven Wrapper and a runnable Postman collection.

## Run locally

You need JDK 17 or newer and Docker (Docker Desktop on Windows and macOS, Docker
Engine on Linux). Set `JAVA_HOME` to your installed JDK. The commands below use
PowerShell. On Linux or macOS, use `./mvnw` instead of `.\mvnw.cmd` and `cp`
instead of `Copy-Item`.

1. If you do not already have a `.env` file, copy the configuration template:

   ```powershell
   Copy-Item .env.example .env
   ```

   The template works as is. Spring and Docker Compose both read `.env`, so the
   database password stays the same in both. Keep `DB_PASSWORD` to letters,
   digits, `-` and `_`: Compose and Spring read `$`, `\`, quotes and ` #`
   differently. The template enables registration and allows session cookies
   over local HTTP. Keep this file private.

2. Start PostgreSQL 18 and Redis:

   ```powershell
   docker compose up -d --wait
   ```

   The command returns once both services are healthy. It creates the `todo`
   database for the API and the `todo_integration` database for the tests.

3. Start the API from the project root:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

The API listens at `http://127.0.0.1:8080`. Check `GET /` for application status.
Flyway applies database migrations on startup.

In IntelliJ, open `pom.xml` as a Maven project and run
`SalemTodoDevOpsApplication` with the project root as its working directory.
Reload Maven after dependency changes and restart the API after changing configuration.

### Local services

| Task | Command |
| --- | --- |
| Start | `docker compose up -d --wait` |
| Stop and keep data | `docker compose down` |
| Reset all data | `docker compose down -v` |

Both services listen on `127.0.0.1` only: PostgreSQL on 5432 and Redis on 6379.
The API needs both: Redis holds the login sessions and the authentication
rate-limit counters.

- **Port 5432 already in use:** another PostgreSQL is running on this machine.
  Stop it, or use the setup without Docker below.
- **Password authentication failed after changing `DB_PASSWORD`:** the database
  keeps the password from its first start. Reset with `docker compose down -v`.
- **`todo_integration` does not exist:** the database volume was created before
  this setup. Reset with `docker compose down -v`.

### Without Docker

Install PostgreSQL 18 and create a database named `todo`. For a standard Windows
installation:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\createdb.exe' -h 127.0.0.1 -U postgres -W todo
```

Then set `DB_PASSWORD` in `.env` to your database password, and adjust `DB_URL`
and `DB_USERNAME` if needed.

Also install Redis and have it listen on `127.0.0.1:6379`, or point
`spring.data.redis.host` and `spring.data.redis.port` at your Redis. Without
Redis, login, registration, `GET /auth/csrf`, and every logged-in request
return 503.

## Use the API

Select **Salem Todo - Local Authentication** in Postman, then open
**Salem Todo DevOps → 00 Authentication → 01 Session**. Send **Get CSRF token**,
**Register account**, and **Login** in order. If the account already exists, log in
with its credentials.

The collection refreshes CSRF tokens automatically before writes. Keep the cookie
jar enabled and use the same host for all requests. The Postman guide covers
manual sends, collection runs, and environment setup.

| Resource | Base route | Capabilities |
| --- | --- | --- |
| Accounts | `/auth` | Register, log in, read current account, log out, change password |
| Projects | `/projects` | Create, list, read, update, archive |
| Tasks | `/projects/{projectId}/tasks` | Manage tasks, filter results, browse pages |
| Labels | `/projects/{projectId}/labels` | Manage project labels and associate them with tasks |
| Members | `/projects/{projectId}/members` | List members, add by email, change roles, remove members, leave |

Create a project before adding tasks. Tasks support status, type, priority,
due date, and labels. Archival preserves records for later reads.

Projects are private to their members. The account that creates a project
becomes its owner. Project responses include `createdByAccountId` and `role`,
the caller's role in that project:

| Role | Can do |
| --- | --- |
| `OWNER` | Everything, including updating and archiving the project |
| `MEMBER` | Read the project; manage tasks and labels |
| `VIEWER` | Read only |

`GET /projects` lists only the caller's projects. For a project the caller does
not belong to, every endpoint returns 404, the same as for a missing project, so
the API never reveals that it exists. A member whose role does not allow an
action receives 403.

Owners add existing accounts by email, change roles, and remove members. Any
member can leave a project by removing their own membership. A project always
keeps at least one active owner, so removing, demoting, or leaving as the last
owner returns 409. Adding an unknown or disabled email returns 422.

Business endpoints require login. Sessions expire after 30 minutes of inactivity
or 12 hours total. They are stored in Redis, so they survive API restarts and
are shared when several API instances run. Password changes revoke existing
logins on every instance. If Redis is unavailable, any request that needs its
session returns 503 (`Authentication is temporarily unavailable.`); requests
without a session, such as `GET /`, keep working.

Upgrading an existing database to schema V8 requires an empty `projects` table,
because older projects have no recorded owner. Reset a development database
before upgrading.

Registration is disabled by default. Enable `AUTH_REGISTRATION_ENABLED` for
controlled onboarding. Use `SESSION_COOKIE_SECURE=false` for local HTTP and
`true` for HTTPS deployments.

### Rate limits

Login, registration, and new sessions are rate limited, with counters in Redis
that every API instance shares:

| Limit | Default |
| --- | --- |
| Login attempts per client address | 20 per minute |
| Login attempts per email | 5 per 15 minutes, reset by a successful login |
| Registrations per client address | 5 per hour |
| New sessions (`GET /auth/csrf` without a session) per client address | 30 per minute |

Over a limit, the API returns 429 with a `Retry-After` header (in seconds). The
response is the same for existing and unknown emails. A throttled login never
checks the password. If Redis is unavailable, these routes return 503.

The limits are `app.rate-limit.*` properties in `application.properties`.
For repeated local Postman runs, uncomment the relaxed per-address limits in
`.env`, copied from `.env.example`. To clear
the local counters, run
`docker compose exec redis sh -c "redis-cli --scan --pattern 'rl:*' | xargs -r redis-cli del"`.

The client address is the TCP peer address. IPv6 clients are counted per /64
network, because one client usually controls a whole /64. Behind a reverse proxy, configure
the API to trust the proxy's forwarding headers
(`server.forward-headers-strategy=native`, with the proxy listed as an internal
proxy); otherwise all users share the proxy's address and its limits.

## Tests

Run the database-independent tests:

```powershell
.\mvnw.cmd test
```

The complete suite needs a disposable PostgreSQL database, a user that can
create and drop schemas, and Redis on `127.0.0.1:6379`.
`docker compose up -d --wait` provides all three, with local-only credentials.
Configure the test connection separately from the application database:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/todo_integration'
$env:TEST_DB_USERNAME = 'todo_test'
$env:TEST_DB_PASSWORD = 'todo-test-only'
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it verify
```

Without Docker, point these variables at your own disposable database instead,
and run Redis on `127.0.0.1:6379`.

This runs unit, HTTP, persistence, concurrency, and migration tests. Reports are
written to `target/surefire-reports` and `target/failsafe-reports`.

GitHub Actions runs the full suite for pull requests and pushes to `master`,
and uploads test reports and the application JAR.

## Documentation

- [Postman guide](postman/README.md) — request examples, environments, and runnable scenarios.
- [Architecture](docs/architecture.md) — module boundaries and persistence responsibilities.
- [Contributing](CONTRIBUTING.md) — branches, commit messages, pull requests, and checks.
