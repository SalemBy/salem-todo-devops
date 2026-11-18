# Salem Todo DevOps

A Spring Boot REST API for organizing projects and tasks, with account
authentication, project labels, task filtering, and pagination.

Built with Java 17, Spring Security, Spring Data JPA, PostgreSQL, and Flyway.
The repository includes a Maven Wrapper and a runnable Postman collection.

## Run locally

You need JDK 17 or newer and PostgreSQL 18. Set `JAVA_HOME` to your installed JDK.
The commands below use PowerShell. On Linux or macOS, use `./mvnw` instead of
`.\mvnw.cmd`.

1. Start PostgreSQL and create a database named `todo`. For a standard Windows
   installation:

   ```powershell
   & 'C:\Program Files\PostgreSQL\18\bin\createdb.exe' -h 127.0.0.1 -U postgres -W todo
   ```

2. If you do not already have a `.env` file, copy the configuration template:

   ```powershell
   Copy-Item .env.example .env
   ```

   Set `DB_PASSWORD` in `.env` to your database password. Adjust `DB_URL` and
   `DB_USERNAME` if needed. The template enables registration and allows session
   cookies over local HTTP. Keep this file private.

3. Start the API from the project root:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

The API listens at `http://127.0.0.1:8080`. Check `GET /` for application status.
Flyway applies database migrations on startup.

In IntelliJ, open `pom.xml` as a Maven project and run
`SalemTodoDevOpsApplication` with the project root as its working directory.
Reload Maven after dependency changes and restart the API after changing configuration.

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

Create a project before adding tasks. Tasks support status, type, priority,
due date, and labels. Archival preserves records for later reads.

Business endpoints require login. Sessions expire after 30 minutes of inactivity
or 12 hours total, and server restarts sign users out. Password changes revoke
existing logins. Authenticated accounts currently share access to business data.
Project membership permissions are planned.

Registration is disabled by default. Enable `AUTH_REGISTRATION_ENABLED` for
controlled onboarding. Use `SESSION_COOKIE_SECURE=false` for local HTTP and
`true` for HTTPS deployments.

## Tests

Run the database-independent tests:

```powershell
.\mvnw.cmd test
```

For the complete suite, provision a dedicated disposable PostgreSQL database and
a user with permission to create and drop schemas. Configure the test connection
separately from the application database:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/todo_integration'
$env:TEST_DB_USERNAME = 'todo_test'
$testCredential = Get-Credential -UserName todo_test -Message 'Test database credentials'
$env:TEST_DB_PASSWORD = $testCredential.GetNetworkCredential().Password
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it verify
```

This runs unit, HTTP, persistence, concurrency, and migration tests. Reports are
written to `target/surefire-reports` and `target/failsafe-reports`.

GitHub Actions runs the full suite for pull requests and pushes to `master`,
and uploads test reports and the application JAR.

## Documentation

- [Postman guide](postman/README.md) — request examples, environments, and runnable scenarios.
- [Authentication contract](docs/authentication-contract.md) — endpoints, validation, sessions, and CSRF.
- [Task filtering contract](docs/task-filter-contract.md) — filters and pagination behavior.
- [Architecture](docs/architecture.md) — module boundaries and persistence responsibilities.
- [Contributing](CONTRIBUTING.md) — branches, commit messages, pull requests, and checks.
