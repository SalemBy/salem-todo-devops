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
