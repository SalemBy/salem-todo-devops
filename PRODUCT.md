# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

- Backend: Spring Boot 4 / Java 17 REST API, PostgreSQL, Flyway, Spring Security with server-side sessions stored in Redis.
- Web UI (being built, #74): React + TypeScript (strict) + Vite in `web/`, with React Router, TanStack Query, Radix primitives and CSS Modules on the design tokens in `DESIGN.md`. A single-page app that calls the API under `/api`; in development the Vite server forwards `/api` to the API, so the browser sees one origin.

## Users

The target user is a team delivering large, complex software projects: many people, many work items, and many repositories, working with AI in their daily flow.

The product reaches that target in stages. It starts with solo developers and small collaborations: a developer working alone or with a few invited people, who lives in GitHub and wants planning next to the code. Each stage grows the product toward large teams without leaving these first users behind.

## Product Purpose

Salem Todo DevOps is a work-management system in the spirit of Azure DevOps: projects, tasks and labels for planning, tracking and delivering software work. "DevOps" refers to that kind of product, not to CI/CD or infrastructure tooling.

Its goal is high delivery on large, complex projects, with AI built in. AI is a core part of how work gets planned, tracked and delivered, not an add-on to a traditional task list. The product is designed so that AI and people work on the same projects, tasks and history.

## Positioning

AI-integrated and GitHub-native, built for large, complex delivery:

- **AI-integrated:** work items, history and links to code carry enough structure and context for AI to read them, reason about them and act on them, within the same permissions as people.
- **GitHub-native:** work items, branches and pull requests belong together, so planning stays tied to the code. The GitHub integration is on the roadmap (#15 repository connections, #16 issue sync, #17 issue-linked branches and PR tracking) and does not exist yet.
- **Grows from small to large:** the same product serves a solo developer today and a large team later, without a rebuild.

## Operating Context

- Used alongside GitHub repositories, AI tools and a developer's normal coding workflow.
- Today the API is used through the Postman collection at `postman/collections/todo`. The web UI has its foundation (design system, app shell, API layer); its screens arrive issue by issue under #74.
- Today collaboration is small-scale: an owner invites a few people by email. The target is many people and many projects working at the same time.

## Capabilities and Constraints

Built:
- Accounts: register (off by default, controlled by configuration), verify the email address before the account counts, log in, log out, change password, read the current account. Sessions expire after 30 minutes idle or 12 hours total. Changing a password ends every session. Public authentication endpoints and password changes are rate-limited.
- Administration: administrators (configured by email) deactivate accounts and give ownerless projects a new owner.
- Projects: create, list, read, update, archive. Each project has a short uppercase key (2–10 characters, unique across the system). Archived projects stay readable but reject writes.
- Tasks: they exist only inside a project. Each has a title, description, status (PENDING, IN_PROGRESS, COMPLETED), type (TASK, BUG, FEATURE, ENHANCEMENT, CHORE), priority (LOW, MEDIUM, HIGH, CRITICAL), a due date, labels and an assignee (a member whose role can write tasks). Task lists filter by status, type, priority, label, assignee and due-date range, in pages of up to 100.
- Comments: members comment on tasks; authors edit their own, and owners and managers can delete any.
- Activity history: every change to projects, tasks, members, comments and labels is recorded with who made it and what changed, readable per project and per task.
- Labels: one set per project, with names unique regardless of case, and archive instead of delete.
- Membership: projects are private. Roles are OWNER, MANAGER, MEMBER and VIEWER, and permissions decide what each can do. A MANAGER runs the work and the team, while editing or archiving the project and managing owners and managers stay with OWNERs. A non-member sees "not found", never "forbidden". A project always keeps at least one active owner.

Roadmap (not built): the web UI screens (#76–#79, then #80–#86), parent-child tasks and dependencies (#13), iterations and estimates (#14), GitHub integration (#15–#17), progress reports (#18–#19), workflow automation (#20), profile pictures (#39), custom roles (#42). The AI capabilities still have to be designed and added to this roadmap.

Known limits on the way to large projects:
- Every task, label and member write locks the project's database row, so writes inside one project happen one at a time. This is fine for small groups but becomes a bottleneck for large teams.
- Task lists sort by one key only, and there is no search yet (#66).
- A task number is its global database ID, not a per-project number.

Constraints the UI must respect:
- Authentication uses session cookies with CSRF protection. The client fetches a token from `GET /auth/csrf` and sends it on every write, and fetches a new one after login or logout. The UI must never store tokens or session IDs in localStorage.
- The API defines no cross-origin (CORS) policy. The UI must be served from the same origin as the API, or through a development proxy, unless a CORS policy is decided later.
- Errors come back as `application/problem+json`, with per-field validation errors in `errors`.
- Records are archived, not deleted, and archived data stays visible.

Terms: Project, project key, Task, Label, Member, Owner/Manager/Member/Viewer, assignee, comment, activity, archive.

Open decisions:
- whether new users can register publicly (it is a configuration switch today);
- how the UI is hosted relative to the API.

## Evidence on Hand

- API behavior: `README.md` and `docs/architecture.md`.
- A working Postman collection: `postman/collections/todo`.
- The visual system: `DESIGN.md` and the tokens in `web/src/styles/tokens.css`.
- None of these exist yet: finished UI screens, AI features, screenshots, a logo or brand assets, real users, testimonials or usage data. Future work must not invent them.

## Product Principles

1. Built for large, complex delivery. Every design choice should hold up for many people, many projects and many work items, even when today's users are a solo developer or a small group.
2. AI works with the product. Work items, history and links to code are structured so AI can understand and act on them, and AI goes through the same permissions and keeps the same history as people.
3. Planning stays next to the code. Each feature should strengthen the link between work items and the GitHub repositories, branches and pull requests they belong to.
4. Grow in stages, without a rebuild. Each stage ships something real and usable, builds on what exists, and keeps the small case simple.
5. Private, safe and traceable. Projects are visible only to their members, permissions are strict, and history is archived, never deleted, so both people and AI actions can be traced.
