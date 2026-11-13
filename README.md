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

After changing Maven dependencies, reload all Maven projects in IntelliJ and
fully stop/restart the application. An already running JVM keeps its original
classpath; recompiling source alone does not add new validation libraries.
Validation is mandatory: the application now refuses to start without a working
Bean Validation provider instead of serving requests without field checks.

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

Required task titles and project names must contain visible text. Omitted,
null, empty, whitespace-only values, Unicode separator-only values (including
non-breaking spaces), and zero-width format-only values are rejected on both
create and update. Project key validation also rejects blank values. Validation
is enforced at HTTP and Spring-managed service entry points using DTO constraints.
Project input fields are `name` and `key`; task input uses `title`.

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

## Projects

Projects are independent of tasks in this first version. Task association is
planned separately in issue #3; the existing Task API is unchanged.

| Endpoint | Behavior |
|---|---|
| `POST /projects` | Creates an active project; returns 201, a project DTO, and a Location header |
| `GET /projects` | Returns active projects by default |
| `GET /projects?active=true`, `false`, or `all` | Filters projects; returns an array, including `[]` for no matches |
| `GET /projects/{id}` | Returns an active or archived project, or 404 if unknown |
| `PUT /projects/{id}` | Replaces name/description; returns 200, or 404 for unknown/archived projects |
| `DELETE /projects/{id}` | Archives a project; returns 204, or 404 if unknown |

Create a project:

```json
{"name":"Todo Platform","key":"TODO","description":"Project management backend"}
```

Name is required and nonblank (maximum 200 characters). Key is required, unique,
and immutable: 2–10 uppercase ASCII letters or digits, starting with a letter.
Lowercase keys are rejected rather than normalized. `LEGACY` is reserved for
the future existing-task migration and cannot be created through the API.
Description is optional (maximum 2000 characters). Project names can repeat.
Invalid inputs, malformed JSON/IDs, and invalid active filters return 400 using
the existing problem-details contract. Duplicate keys return 409, including
concurrent creation attempts and reuse of an archived project's key.

Update a project using only the editable fields:

```json
{"name":"Updated project name","description":"Updated details"}
```

PUT requires name; omitted/null description clears it. ID, key, active, and
creation time are server-managed and cannot be changed through the request.
Each response includes `id`, `name`, `key`, `description`, `active`, `createdAt`,
and `updatedAt`. DELETE preserves the row and details, changes active to false,
and updates the modification time on the first transition. Repeated DELETE is
successful without changing the modification time again. Archived projects
remain readable and their keys stay reserved. No restore operation is provided.

Flyway migration V3 creates `projects` with a database uniqueness constraint.
It does not modify existing task rows or create project/task relationships.
The same project HTTP contract runs against H2 and PostgreSQL; PostgreSQL tests
also verify upgrading a V2 database with existing active and inactive tasks.

The separate `postman/collections/projects` collection runs a create/read/list/
update/archive scenario and negative checks with captured IDs and a generated
key. Run it only against a disposable development or test database: it leaves
one archived project per run.

```powershell
postman collection run postman/collections/projects
```

## API errors

Controller and routing errors use `Content-Type: application/problem+json`.
All error responses include `type`, `title`, `status`, `detail`, `instance`
(request path without query parameters), and `errors` (an array, empty when
there are no field-level violations). Error types are stable URNs based on HTTP
status, such as `urn:projetotodo:problem:400`.

For example, posting a blank title returns HTTP 400:

```json
{
  "type": "urn:projetotodo:problem:400",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/tasks",
  "errors": [{"field": "title", "message": "Title is required"}]
}
```

Malformed JSON, missing bodies, invalid enum values, and invalid parameter types
return 400. Unknown resources return 404, unsupported methods return 405, and
unsupported request media types return 415. Unexpected failures return 500 with
a generic detail; exception messages, stack traces, and rejected values are not
returned to clients. Relevant protocol headers such as `Allow` are preserved.

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
The default build verifies H2 behavior. PostgreSQL integration tests are opt-in
locally and mandatory in CI.

### PostgreSQL integration tests

Provision a dedicated, disposable PostgreSQL 18 database. Never point the test
suite at the application's real database. The test user needs permission to
create/drop schemas. Supply its connection details separately from `DB_*`:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/todo_integration'
$env:TEST_DB_USERNAME = 'todo_test'
$testCredential = Get-Credential -UserName todo_test -Message 'Test database credentials'
$env:TEST_DB_PASSWORD = $testCredential.GetNetworkCredential().Password
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it verify
```

This runs the same task HTTP and error contract against H2 and PostgreSQL, then
tests a PostgreSQL V1-to-V2 migration with an existing task. The migration test
uses a randomly named schema and removes only that schema afterward. It checks
data preservation and PostgreSQL not-null/check constraints. The HTTP suite
expects an otherwise empty test database and cleans up records it creates.
Missing PostgreSQL configuration or unavailable PostgreSQL fails the opt-in build;
integration tests are not silently skipped. Maven Failsafe writes PostgreSQL
reports to `target/failsafe-reports`; H2 reports remain in `target/surefire-reports`.

To test and package the application in one command:

```powershell
.\mvnw.cmd -B --no-transfer-progress verify
```

On Linux or macOS, use `./mvnw` instead of `.\mvnw.cmd`.

## Continuous integration

The `Java CI` GitHub Actions workflow runs on pushes and pull requests to
`master`, and can also be started manually from the Actions tab. It installs
Temurin Java 17, caches Maven dependencies, starts a disposable PostgreSQL 18
service, and runs `./mvnw -B --no-transfer-progress -Ppostgres-it verify`.
CI uses test-only database credentials; application database secrets are not needed.

Test reports are uploaded even if the build fails (when reports exist). A
successful build also uploads the executable JAR. Artifacts are retained for
14 days and can be downloaded from the workflow run. Deployment is not configured.

## Development workflow

Use GitHub issues to define features and bugs, then work on a feature branch and
open a pull request to `master`. Include `Closes #<issue-number>` in the PR
description and wait for CI before merging. See [CONTRIBUTING.md](CONTRIBUTING.md)
for the full workflow and the current branch protection limitation.
