# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

- Backend (exists): Spring Boot 4 / Java 17 REST API, PostgreSQL, Flyway, Spring Security with server-side sessions.
- Web UI (not built yet): React + TypeScript, built with Vite, as a separate single-page app that calls the REST API.

## Users

Solo developers organizing their own software projects and tasks, sometimes with a few collaborators they invite to a project. They are developers who live in GitHub and want planning that sits next to their code, not a heavyweight enterprise tool.

## Product Purpose

Salem Todo DevOps is a work-management tool in the spirit of Azure DevOps: projects, tasks and labels for planning and tracking software work. "DevOps" refers to that kind of product, not to CI/CD or infrastructure tooling. Success means a developer can plan and follow their work without leaving the context of their repositories.

## Positioning

A GitHub-native workflow: work items, branches and pull requests belong together, so planning stays tied to the code. This is the trait future work should protect. The GitHub integration itself is on the roadmap (#15 repository connections, #16 issue sync, #17 issue-linked branches and PR tracking) and does not exist yet.

## Operating Context

- Used alongside GitHub repositories and a developer's normal coding workflow.
- Today the API is used through the Postman collection at `postman/collections/todo`. No UI exists yet.
- Collaboration is small-scale: an owner invites a few people by email.

## Capabilities and Constraints

Built:
- Accounts: register (off by default, controlled by configuration), log in, log out, change password, read the current account. Sessions expire after 30 minutes idle or 12 hours total. Changing a password ends every session.
- Projects: create, list, read, update, archive. Each project has a short uppercase key (2–10 characters, unique across the system). Archived projects stay readable but reject writes.
- Tasks: they exist only inside a project. Each has a title, description, status (PENDING, IN_PROGRESS, COMPLETED), type (TASK, BUG, FEATURE, ENHANCEMENT, CHORE), priority (LOW, MEDIUM, HIGH, CRITICAL), a due date and labels. Task lists filter by status, type, priority, label and due-date range, in pages of up to 100.
- Labels: one set per project, with names unique regardless of case, and archive instead of delete.
- Membership: projects are private. Roles are OWNER, MEMBER and VIEWER, and permissions decide what each can do. A non-member sees "not found", never "forbidden". A project always keeps at least one active owner.

Roadmap (not built): task assignment (#10), comments (#11), activity history (#12), parent-child tasks and dependencies (#13), iterations and estimates (#14), GitHub integration (#15–#17), progress reports (#18–#19), workflow automation (#20), profile pictures (#39), custom roles (#42).

Constraints the UI must respect:
- Authentication uses session cookies with CSRF protection. The client fetches a token from `GET /auth/csrf` and sends it on every write, and fetches a new one after login or logout. The UI must never store tokens or session IDs in localStorage.
- The API defines no cross-origin (CORS) policy. The UI must be served from the same origin as the API, or through a development proxy, unless a CORS policy is decided later.
- Errors come back as `application/problem+json`, with per-field validation errors in `errors`.
- Records are archived, not deleted, and archived data stays visible.

Terms: Project, project key, Task, Label, Member, Owner/Member/Viewer, archive.

Open decisions: whether new users can register publicly (it is a configuration switch today); how the UI is hosted relative to the API.

## Evidence on Hand

- API contracts: `docs/authentication-contract.md`, `docs/project-membership-contract.md`, `docs/task-filter-contract.md`, `docs/architecture.md`.
- A working Postman collection: `postman/collections/todo`.
- None of these exist yet: a UI, screenshots, a logo or brand assets, real users, testimonials or usage data. Future work must not invent them.

## Product Principles

1. Planning stays next to the code. Each feature should strengthen the link between work items and the GitHub repositories, branches and pull requests they belong to.
2. Built for one developer first. Defaults suit a single person working alone, and collaboration is added on top without making the solo case harder.
3. Private and safe by default. Projects are visible only to their members, and permissions are strict.
4. Keep the history. Archive instead of delete, so past work stays readable.
5. Fewer concepts than enterprise tools. Add a concept only when the solo workflow clearly needs it.
