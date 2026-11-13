# Postman and Codex

Install Postman CLI and verify that `postman --version` works in your terminal.
Restart IntelliJ after changing PATH so new terminals inherit it.

Start the Spring Boot application, then run from the project root:

```powershell
postman collection run postman/collections/todo
```

The collection includes requests that create, update, and soft delete tasks.
Run it against a disposable development or test database. Set the collection's
`taskId` variable to a task you intend to update or deactivate; it currently
defaults to `1`. Requests do not automatically reuse the ID from creation.
To use a different port:

```powershell
postman collection run postman/collections/todo --env-var baseUrl=http://127.0.0.1:8081
```

For desktop use, open this project through Postman's Native Git workflow.
The root `AGENTS.md` and `postman/skills/` provide Codex's CLI integration.
The `.postman/resources.yaml` file records the workspace binding. Account login
is local to each developer; credentials must not be committed.

## Project CRUD checks

Run the project-only scenario separately:

```powershell
postman collection run postman/collections/projects
```

It generates a valid unique project key, captures the created project ID, and
reuses it for read, update, and archive requests. It verifies active filters,
duplicate/reserved keys, archived reads, rejected archived updates, and repeated
archive behavior. Successful and conflict examples are saved alongside requests.
No hardcoded existing project ID is used. Run the collection in order; individual
requests require the variables from creation. Each run leaves one archived
project in the selected database, so use disposable test data.

## Required field regressions

Each request tests one invalid field while keeping other fields valid. The data
file covers omitted, null, empty, ordinary whitespace, non-breaking/figure/narrow
spaces, and zero-width spaces. Run against a disposable test database:

```powershell
postman collection run postman/collections/validation-regressions -d postman/documents/required-fields-cases.json
```

All 27 requests must return 400 with an error for the tested field. If a
regression returns 201, this run can leave invalid test records. Automated
HTTP/service tests also assert that rejected input does not change database rows.
