# Modular monolith

Salem Todo DevOps is one deployable Spring Boot application with one PostgreSQL database.
The `workmanagement` module owns projects, tasks and their tables.
The `identity` module owns accounts, validation, password hashing, and session
authentication. Its API exposes registration, login, current account, logout, and
password change. Security infrastructure loads session identity, validates account
state and credential version before CSRF, and protects business routes by default.
Session principals contain no credential material. Password changes lock the account
and increment its credential version, revoking existing sessions. Membership-based
authorization belongs to #9. The module follows api/application/domain/infrastructure boundaries.
Projects and tasks are separate entities within this module; each task has one immutable project association.

## Responsibilities

- `workmanagement.api`: HTTP controllers; exposes application DTOs, never entities.
- `workmanagement.application`: transactional use cases and request/response DTOs; coordinates repositories and domain objects.
- `workmanagement.domain`: Project, Task and TaskStatus; JPA mappings are kept here intentionally. No HTTP or Spring dependencies.
- `workmanagement.infrastructure.persistence`: Spring Data repositories, queried by application services.
- `shared`: common validation, configuration, error handling and application status endpoint. It cannot depend on workmanagement.

This is a pragmatic layered module. Application services currently use Spring Data repositories directly; repository ports can be introduced when alternate persistence or more complex domain behavior justifies them.
Future modules must use the application services and DTOs of workmanagement rather than accessing its repositories or entities. Do not add placeholder identity or permission modules before implementing those features.

ArchUnit tests enforce domain independence, shared independence and controller boundaries. API controllers depend on application services; application services cannot depend on controllers.

## Ownership and lifecycle

Every task references an existing project. No reassignment method or request field is exposed; the JPA association is not updatable. An extra projectId in an update payload is ignored and never moves the task.
Project archival changes only the project. Task writes in archived projects return 409; in an active project, updates to already inactive tasks return 404. Archived projects and their tasks remain readable.
Task reads and writes require projectId in the URL and query tasks by both task ID and project ID. Creation derives ownership from the URL. Global task endpoints are removed.
Task writes lock their owning project before loading the task, and project archival takes the same database lock, serializing these operations. The lock is held through transaction completion.

V4 adds a required project foreign key and index. It requires an empty pre-association tasks table and creates no projects automatically. A populated old tasks table fails migration instead of silently discarding data. The development database reset is a one-time operation, not application startup behavior. Applied migrations V1–V3 are unchanged.

Membership, authorization, task moves and pagination are outside issue #3.
