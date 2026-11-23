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

The services listen on `127.0.0.1` only: PostgreSQL on 5432, Redis on 6379, and
Mailpit on 1025 (SMTP) and 8025 (web inbox). The API needs PostgreSQL and Redis:
Redis holds the login sessions and the authentication rate-limit counters.
Mailpit receives the verification emails, so open http://127.0.0.1:8025 to read
them; nothing reaches real inboxes.

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
**Register account**, **Verify account email**, and **Login** in order. The verify
request reads the token from Mailpit. If the account already exists, log in with
its credentials.

### Email verification

A new account must verify its email address before it can log in.

| Request | Result |
| --- | --- |
| `POST /auth/register` | 201 with the account, `emailVerified: false`; sends an email with a link |
| `POST /auth/verify` with `{"token": "...", "password": "..."}` | 204; the account can log in |
| `POST /auth/verification-email` with `{"email": "..."}` | 202 with no body, for every email |

- The link is `AUTH_VERIFICATION_URL?token=...`; the page behind it asks for the
  password chosen at registration and posts both to `/auth/verify`. Proving the
  inbox and the password together means nobody who re-registers a pending email
  can have its owner verify the account for them. A token works once and expires
  after 24 hours. Unknown, expired and used tokens, and a wrong password, get 400
  `Verification link is invalid or has expired`.
- Until it is verified, an account cannot log in (401 `Invalid credentials`,
  like a wrong password), cannot be added to a project, and is not an
  administrator. Account responses include `emailVerified`.
- Asking for the email again sends a new link to a pending account, and the old
  link stops working. The response is the same for unknown and verified emails.
- Registering a pending email again replaces that registration (name, password
  and link), so nobody can hold someone else's address. A verified email still
  gets 409. Registrations nobody verifies are deleted after 7 days.
- Emails are sent in the background, with 5-second SMTP timeouts. If one cannot
  be sent, registration still succeeds; ask for it again.
- Mail goes over SMTP: `MAIL_HOST` (default `127.0.0.1`), `MAIL_PORT` (`1025`),
  optional `MAIL_USERNAME` and `MAIL_PASSWORD`, and `MAIL_FROM`. Accounts that
  existed before verification was added count as verified.

The collection refreshes CSRF tokens automatically before writes. Keep the cookie
jar enabled and use the same host for all requests. The Postman guide covers
manual sends, collection runs, and environment setup.

| Resource | Base route | Capabilities |
| --- | --- | --- |
| Accounts | `/auth` | Register, verify the email, log in, read current account, log out, change password |
| Projects | `/projects` | Create, list, read, update, archive |
| Tasks | `/projects/{projectId}/tasks` | Manage tasks, assign them, discuss them in comments, filter results, browse pages |
| Labels | `/projects/{projectId}/labels` | Manage project labels and associate them with tasks |
| Members | `/projects/{projectId}/members` | List members, add by email, change roles, remove members, leave |
| Activity | `/projects/{projectId}/activity` | Read who changed what and when, for the project or one task |

Create a project before adding tasks. Tasks support status, type, priority,
due date, and labels. Archival preserves records for later reads.

Projects are private to their members. The account that creates a project
becomes its owner. Project responses include `createdByAccountId` and `role`,
the caller's role in that project:

| Role | Can do |
| --- | --- |
| `OWNER` | Everything, including updating and archiving the project and managing owners and managers |
| `MANAGER` | Read the project; manage tasks and labels; moderate comments; add, remove and change `MEMBER`s and `VIEWER`s |
| `MEMBER` | Read the project; manage tasks and labels |
| `VIEWER` | Read only |

`GET /projects` lists only the caller's projects. For a project the caller does
not belong to, every endpoint returns 404, the same as for a missing project, so
the API never reveals that it exists. A member whose role does not allow an
action receives 403.

Owners and managers add existing accounts by email, change roles, and remove
members. Only an owner can add, change or remove an `OWNER` or `MANAGER`, or
grant those roles; a manager who tries gets 403 `Only an owner can manage
owners and managers` and nothing changes. Any member can leave a project by
removing their own membership. A project always
keeps at least one active owner, so removing, demoting, or leaving as the last
owner returns 409. Adding an unknown or disabled email returns 422.

A task can have one assignee, an active `OWNER`, `MANAGER` or `MEMBER` of its project.
Assignment records responsibility only; it never grants access.

| Request | Result |
| --- | --- |
| `PUT /projects/{projectId}/tasks/{id}/assignee` with `{"accountId": 42}` | 200 with the task; `assigneeId` is set |
| `DELETE /projects/{projectId}/tasks/{id}/assignee` | 204; also when the task has no assignee |
| `GET /projects/{projectId}/tasks?assigneeId=42` | that account's tasks; `assigneeId=none` lists unassigned tasks |

Assigning needs the same role as editing tasks. Assigning a viewer, a
non-member, or an unknown or disabled account returns 422. Every task response
includes `assigneeId` (null when unassigned). Removing a member, a member
leaving, or demoting one to `VIEWER` clears their assignments in that project.
Archived tasks keep their assignee as history until that person loses edit
rights in the project.

Members discuss a task through comments at
`/projects/{projectId}/tasks/{taskId}/comments`:

| Request | Who | Result |
| --- | --- | --- |
| `GET` (`?page=&size=`) | any member | 200 page of comments, oldest first |
| `POST` with `{"body": "..."}` | `OWNER`, `MANAGER`, `MEMBER` | 201 with the comment and its `Location` |
| `GET /{commentId}` | any member | 200 with the comment |
| `PUT /{commentId}` with `{"body": "..."}` | the author | 200 with the edited comment |
| `DELETE /{commentId}` | the author, an `OWNER` or a `MANAGER` | 204 |

A comment holds `id`, `taskId`, `authorId`, `body`, `createdAt`, and
`updatedAt`. The body is plain text of 1 to 10000 characters, stored exactly as
sent and never rendered. The author and creation time never change; owners and
managers moderate by deleting, never by editing someone else's words. An author who is
demoted to `VIEWER` or leaves can no longer edit or delete their comments,
which stay visible. Deleting hides a comment from every endpoint (404) but
keeps it in the database for audit; deleted comments cannot be read through the
API. Archived projects (409) and archived tasks (404) are read-only, and their
comments stay readable.

### Activity history

Every successful change to project work is recorded in the same transaction as
the change, with the account that made it. Refused or rolled-back requests, and
requests that change nothing (the same values again, archiving something already
archived), record nothing. Any member, including a `VIEWER`, reads the history,
newest first and paged (`?page=&size=`, size 1 to 100, default 20):

| Request | Result |
| --- | --- |
| `GET /projects/{projectId}/activity` | 200 page of every event in the project |
| `GET /projects/{projectId}/tasks/{taskId}/activity` | 200 page of the task's events and its comments' events; 404 `Task not found` for a task outside the project |

Each event holds `id`, `projectId`, `taskId` (task and comment events),
`actorId`, `resourceType`, `resourceId`, `action`, `occurredAt` (UTC), and
`changes`, a list of `{field, before, after}`:

| `resourceType` | `action` | Recorded fields |
| --- | --- | --- |
| `PROJECT` | `CREATED`, `UPDATED`, `ARCHIVED` | `name`, `key`, `description` |
| `TASK` | `CREATED`, `UPDATED`, `ARCHIVED` | `title`, `description`, `status`, `type`, `priority`, `dueDate`, `labelIds`, `assigneeId` |
| `MEMBER` | `ADDED`, `UPDATED`, `REMOVED` | `role` (`resourceId` is the member's account ID) |
| `COMMENT` | `CREATED`, `UPDATED`, `DELETED` | `body` |
| `LABEL` | `CREATED`, `UPDATED`, `ARCHIVED` | `name` |

- Values are strings: enum names, `yyyy-MM-dd` dates, account IDs, and label IDs
  ascending and comma-separated (`"3,7"`); `null` means none.
- `description` and `body` are recorded only as changed: `before` and `after` are
  always `null`, so the history never copies text people wrote. No passwords,
  tokens or other secrets are recorded.
- Status changes, label changes and assignments are `TASK` `UPDATED` events.
  Removing a member or demoting them to `VIEWER` records an unassignment for each
  of their tasks, by the account that removed them; an administrator's
  deactivation or owner recovery records the administrator.
- Events cannot be edited or deleted (other methods return 405). They stay
  readable after the project or task is archived or the actor leaves, and are
  kept indefinitely. Names come from `/members` and `/labels?active=all`.

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

Login, registration, new sessions, and password changes are rate limited, with counters in Redis
that every API instance shares:

| Limit | Default |
| --- | --- |
| Login attempts per client address | 20 per minute |
| Login attempts per email | 5 per 15 minutes, reset by a successful login |
| Registrations per client address | 5 per hour |
| New sessions (`GET /auth/csrf` without a session) per client address | 30 per minute |
| Password changes per login | 5 per 15 minutes |
| Password changes per client address | 10 per minute |
| Verification emails per client address | 5 per hour |
| Verification emails per email | 3 per hour |
| Email verifications (`POST /auth/verify`) per client address | 20 per minute |

Over a limit, the API returns 429 with a `Retry-After` header (in seconds). The
response is the same for existing and unknown emails. A throttled login never
checks the password. If Redis is unavailable, these routes return 503.

The password-change limit counts per login, not per account. Someone holding a
stolen session can only use up that session's attempts, so the owner can still
change the password from their own login, which ends every session. Because a
new login starts a new count, the per-address limit caps how many password
checks one address can cause.

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

### Administration

System administrators can turn accounts off and on and give a project with no
active owner a new owner. They are the active accounts whose email is listed in
`ADMIN_EMAILS` (comma-separated, case-insensitive; empty by default). Nothing is
seeded, and a change needs a restart. A listed email counts only once its
account is verified, so only the person who controls that address can hold the
role.

| Route | Result |
| --- | --- |
| `POST /admin/accounts/{accountId}/deactivate` | 200, see below; 404 `Account not found`; 409 `At least one active administrator is required` |
| `POST /admin/accounts/{accountId}/reactivate` | 200 with the account; 404 `Account not found` |
| `POST /admin/projects/{projectId}/owners` with `{"accountId": 7}` | 200 with the new owner's membership; 404 `Project not found`; 409 `Project already has an active owner`; 422 `Account must be active` |

```json
{
  "account": {"id": 7, "email": "dev@example.com", "displayName": "Dev", "active": false, "createdAt": "...", "updatedAt": "..."},
  "unassignedTasks": 3,
  "ownerlessProjectIds": [12]
}
```

- Deactivation ends every session of the account on every instance, refuses its
  logins, and clears its task assignments in all projects, archived ones
  included. Memberships, comments and authorship are kept. `ownerlessProjectIds`
  lists the projects it owned that now have no active owner.
- Reactivation allows logins again. Old sessions and cleared assignments are not
  restored. Both actions are idempotent.
- Recovery promotes an existing member to `OWNER` or adds the account as one,
  and only for a project with no active owner, archived or not.
- Signed-in non-administrators get the same 404 an unknown route returns, for
  any method or body. Anonymous callers get 401, and writes need a CSRF token.
- Each action is logged at INFO with the administrator, the action and the
  target, until activity history (#12) records them.

## Monitoring

Spring Boot Actuator runs on a separate management port, `127.0.0.1:8081` by
default (`MANAGEMENT_PORT`). The public API on port 8080 serves no `/actuator`
endpoints. Only processes on the same host can reach the management port, so its
endpoints need no login.

| Endpoint | Meaning |
| --- | --- |
| `GET /actuator/health/liveness` | The application is running. Never follows a dependency, so an outage never restarts it. |
| `GET /actuator/health/readiness` | Ready for traffic: PostgreSQL and Redis respond. 503 `{"status":"DOWN"}` otherwise. |
| `GET /actuator/health` | Overall status. Health responses never name components or details. |
| `GET /actuator/info` | Build version and git commit, to match a running instance to a release. |
| `GET /actuator/prometheus` | Metrics in Prometheus format: HTTP requests by route and status, the Hikari connection pool, JVM and process. |

```bash
curl -s http://127.0.0.1:8081/actuator/health/readiness
curl -s http://127.0.0.1:8081/actuator/prometheus | grep hikaricp_connections_active
```

Metric tags never contain emails, account or session IDs, or URLs with IDs.
Routes appear as templates such as `/projects/{id}`. Requests rejected by
security before routing are tagged with their path only for the fixed `/auth/*`
endpoints, and as `UNKNOWN` otherwise.

- When probes or a scraper run outside the host (for example in containers),
  set `MANAGEMENT_SERVER_ADDRESS` to an address on a private network. Never
  expose the management port publicly.
- Never set the management port equal to the server port: that would serve
  these endpoints on the public API.
- Dashboards (Prometheus and Grafana), tracing and structured logs are planned
  as follow-ups.

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
