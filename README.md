# Projeto Todo

Spring Boot REST backend for a to-do application. Uses Java 17, Maven Wrapper
3.9.9, Spring MVC, Spring Data JPA, Flyway, and PostgreSQL.

## Run

In IntelliJ, open `pom.xml` as a Maven project and use **Reload All Maven
Projects** after changing dependencies. Select an installed JDK 17 or newer
as the project SDK and Maven runner JRE.

PostgreSQL must be running on `127.0.0.1:5432`. Create the database once
(enter the password chosen during PostgreSQL installation when prompted):

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\createdb.exe' -h 127.0.0.1 -U postgres -W todo
```

The application also reads `.env` from the project root as a properties file.
This file is excluded from Git. Run the application with the project root as
its working directory.

For file-based configuration, copy the shared template and edit the local copy:

```powershell
Copy-Item .env.example .env
```

Skip the copy if `.env` already exists. Replace the password placeholder in
`.env` with your local database password. Never commit that file.

In the `ProjetoTodoApplication` run configuration, set the environment variable
`DB_PASSWORD` to that password, then run the application. Optional overrides:
`DB_URL` (default `jdbc:postgresql://127.0.0.1:5432/todo`) and `DB_USERNAME`
(default `postgres`). Keep passwords out of committed files. Flyway applies
pending SQL migrations on startup, including task creation and the active flag.

For PowerShell, read the password without displaying it:

```powershell
$dbCredential = Get-Credential -UserName postgres -Message 'PostgreSQL credentials'
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
```

For PowerShell, set `JAVA_HOME` to an installed JDK, then run:

```powershell
$env:JAVA_HOME = 'C:\Users\julio\.jdks\ms-17.0.15'
.\mvnw.cmd spring-boot:run
```

The server listens at `http://127.0.0.1:8080`. `GET /` returns application status.
There is no browser task interface yet.

Create a task with `POST /tasks`, using `Content-Type: application/json`:

```json
{"title": "My first task", "description": "Optional details"}
```

The response is HTTP 201 with the saved task and a Location header. New tasks
have status `PENDING`. Title is required, with a maximum of 200 characters;
description is optional, with a maximum of 2000 characters. Invalid requests
return HTTP 400.

`GET /tasks` returns HTTP 200 with a JSON array of active tasks, or `[]`
when none match. Use `?active=true` for active tasks (the default),
`?active=false` for inactive tasks, or `?active=all` for every task.
Other filter values return HTTP 400. Task responses include an `active` boolean.
New tasks are active; migration V2 also marks existing tasks active.

`PUT /tasks/{id}` replaces an existing task's title, description, and status:

```json
{"title":"Updated task","description":"Updated details","status":"IN_PROGRESS"}
```

Title follows the same rules as creation. Status is required and must be
`PENDING`, `IN_PROGRESS`, or `COMPLETED`. Omitted or null description clears it.
Returns HTTP 200 with the updated task, HTTP 400 for invalid input, or HTTP 404
when the ID does not exist or the task is inactive. ID and creation time are preserved.

`DELETE /tasks/{id}` performs a soft delete: it sets `active=false` and updates
the modification time, preserving the database row and task details. Returns
HTTP 204 with no body, including for an already inactive task, or HTTP 404
for an unknown ID. PUT cannot reactivate or edit an inactive task.
Reading individual tasks is not implemented yet.

## Project folders

- `src/main/java/.../controller`: HTTP endpoints.
- `src/main/java/.../dto`: request and response records.
- `src/main/java/.../service`: task operations and persistence coordination.
- `src/main/java/.../entity`, `enums`, `repository`: database model and persistence.
- `src/main/resources`: application settings and Flyway SQL migrations.
- `src/test`: application, HTTP, validation, and database integration tests.
- `.mvn`, `mvnw`, `mvnw.cmd`: Maven Wrapper configuration and launchers.
- `.idea`: IntelliJ settings; `target`: generated build output.
- `data`: previous H2 database files; existing tasks are preserved there but are
  not automatically transferred to PostgreSQL.
- `.maven-cache`: ignored local Maven dependency cache.
- `.env.example`: shared configuration template; `.env`: ignored local settings.

## Test

```powershell
.\mvnw.cmd test
```

Tests use an in-memory H2 database and a random HTTP port, leaving the local
PostgreSQL database unchanged. Flyway creates the schema, and Hibernate validates it.
These tests verify H2 behavior; they do not establish PostgreSQL connectivity.

To test and package the application in one command:

```powershell
.\mvnw.cmd -B --no-transfer-progress verify
```

On Linux or macOS, use `./mvnw` instead of `.\mvnw.cmd`.

## Continuous integration

The `Java CI` GitHub Actions workflow runs on pushes and pull requests to
`master`, and can also be started manually from the Actions tab. It installs
Temurin Java 17, caches Maven dependencies, and runs `./mvnw -B
--no-transfer-progress verify`. No database credentials are needed: tests use H2.

Test reports are uploaded even if the build fails (when reports exist). A
successful build also uploads the executable JAR. Artifacts are retained for
14 days and can be downloaded from the workflow run. Deployment is not configured.

## Development workflow

Use GitHub issues to define features and bugs, then work on a feature branch and
open a pull request to `master`. Include `Closes #<issue-number>` in the PR
description and wait for CI before merging. See [CONTRIBUTING.md](CONTRIBUTING.md)
for the full workflow and the current branch protection limitation.
