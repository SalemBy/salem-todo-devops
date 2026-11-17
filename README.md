# Salem Todo DevOps

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

In the `SalemTodoDevOpsApplication` run configuration, set the environment variable
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

Create a project first using POST /projects and use its returned ID. Create a task with `POST /projects/{projectId}/tasks`, using `Content-Type: application/json`:

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

`GET /projects/{projectId}/tasks` returns HTTP 200 with a JSON array of active tasks, or `[]`
when none match. Use `?active=true` for active tasks (the default),
`?active=false` for inactive tasks, or `?active=all` for every task in that project.
Other filter values return HTTP 400. Task responses include an `active` boolean.
New tasks are active; migration V2 also marks existing tasks active.

`PUT /projects/{projectId}/tasks/{id}` replaces an existing task's title, description, and status:

```json
{"title":"Updated task","description":"Updated details","status":"IN_PROGRESS"}
```

Title follows the same rules as creation. Status is required and must be
`PENDING`, `IN_PROGRESS`, or `COMPLETED`. Omitted or null description clears it.
Returns HTTP 200 with the updated task, HTTP 400 for invalid input, or HTTP 404
when the ID does not exist or the task is inactive. ID and creation time are preserved.

`DELETE /projects/{projectId}/tasks/{id}` performs a soft delete: it sets `active=false` and updates
the modification time, preserving the database row and task details. Returns
HTTP 204 with no body, including for an already inactive task, or HTTP 404
for an unknown ID. PUT cannot reactivate or edit an inactive task.
GET /projects/{projectId}/tasks/{id} reads active or inactive tasks; an unknown task returns 404.

## Projects

Every task belongs to exactly one project. Project assignment cannot be changed.

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
Lowercase keys are rejected rather than normalized.
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

Project detail updates and archival acquire the same project row lock. If archival
wins, a competing detail update returns 404 without changing the archived record.
If the update wins, its details are retained and archival then leaves the project
inactive. Task creation competing with archival returns 409 when archival wins.
Writes to the same project wait for each other; different projects can be written
independently. PostgreSQL concurrency regressions exercise both update/archive
orderings and verify retained task history.

Flyway migration V3 creates `projects` with a database uniqueness constraint.
It does not modify existing task rows or create project/task relationships.
The project HTTP contract runs against PostgreSQL; migration tests
also verify upgrading a V2 database with existing active and inactive tasks.

The `02 Projects` folder in `postman/collections/todo` runs a create/read/list/
update/archive scenario and negative checks with captured IDs and a generated
key. Run it only against a disposable development or test database: it leaves
one archived project per run.

```powershell
postman collection run postman/collections/todo -i "02 Projects"
```

## API errors

Task titles, project names and optional descriptions reject embedded NUL (U+0000)
on create and update with HTTP 400 and field validation errors. Rejected creates
insert no records; rejected updates preserve the existing record and timestamps.
Other supported Unicode and null descriptions remain valid. Unexpected server
errors retain the generic error response.

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
  "instance": "/projects/1/tasks",
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

The default build runs database-independent architecture, service-validation proxy,
validation-startup and database-error translation tests. HTTP and persistence
checks require the postgres-it profile locally and are mandatory in CI.

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

This runs the fast suite plus PostgreSQL HTTP and migration checks. Flyway applies
the production migrations and Hibernate validates the schema. Migration tests
use randomly named schemas and remove only those schemas afterward. HTTP tests
also use a randomly named schema per cached Spring context, with production migrations.
Fixtures are cleaned after each case and the schema is dropped when the context
closes, including normal test failures. Other schemas are not used for HTTP fixtures.
Tests sharing a context run sequentially; separate Maven processes get different
schemas. A forcibly terminated JVM may leave a test schema behind, so continue using
a disposable database. Missing settings or unavailable PostgreSQL fail the build
rather than silently skipping checks.
Failsafe reports are in `target/failsafe-reports`; fast-test reports are in
`target/surefire-reports`.

To run the fast tests and package the application (without HTTP/database verification):

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

## Product identity

Salem is the company; Todo DevOps is the product. The backend is published as Salem Todo DevOps.

- Repository: https://github.com/SalemBy/salem-todo-devops
- Maven coordinates: github.salemby:salem-todo-devops
- Java package: github.salemby.tododevops
- Entry point: SalemTodoDevOpsApplication

After reloading Maven in IntelliJ, update the run configuration's main class to github.salemby.tododevops.SalemTodoDevOpsApplication and restart the application. The local checkout directory can retain its existing name.

Database names, Flyway migrations and the existing urn:projetotodo:problem error identifiers remain stable for compatibility.

## Project backlogs and modular structure

Every task endpoint requires the project ID in its URL:

| Method | Endpoint |
| --- | --- |
| POST / GET | `/projects/{projectId}/tasks` |
| GET / PUT / DELETE | `/projects/{projectId}/tasks/{id}` |

The project ID is no longer part of the create DTO. The URL determines ownership;
an extra projectId in JSON cannot override it. Responses still include projectId.
The old /tasks routes, including /tasks?projectId=..., return 404.

Lists are filtered in the database and accept active=true (default), false or all,
within that project only. Known empty projects return []; unknown projects or
tasks belonging to a different project return 404. Malformed, fractional or
nonpositive project IDs return 400. A query parameter cannot select another project.

All task writes in an archived project return 409, including updates to inactive tasks.
In an active project, updating an inactive task returns 404. Archival preserves task
state and timestamps. No operation can move a task between projects.

Project IDs in task URLs must use integer notation. Decimal forms (including
`42.0`) and scientific notation return 400 without creating a task, even when
the integer portion identifies a real project. Ownership comes only from the URL;
an obsolete JSON `projectId` field is ignored and cannot select another project.
This supersedes the former global-task API's JSON numeric-coercion behavior (#26).

Task updates and soft deletion acquire the owning project's row lock before
loading task state. If deletion wins, the competing update returns 404 and leaves
the task inactive with its original details. If the update wins, deletion retains
the updated details and leaves the task inactive. PostgreSQL regressions coordinate
both orderings through observed lock waits and check the persisted task state.

Project-scoped routes establish the ownership boundary; they do not implement
user authorization. Accounts, invitations, membership checks and permissions
remain future work. Until those checks exist, knowing a project ID is sufficient
to call its API.

Migration V4 adds mandatory project ownership without creating a system project.
This development version requires an empty pre-association tasks table. This API
change does not require another migration or reset any database.

See [architecture](docs/architecture.md) for the workmanagement module, dependency rules and transaction boundaries.
