# Project membership contract (issue #9)

Design agreed before implementation; fully implemented. This contract replaces
the interim rule from issue #8 that every authenticated account can read and
change all business data.

## Roles and permissions

Each project has members. Each member has one role: OWNER, MEMBER, or VIEWER.
A project can have several owners and must always have at least one.

| Action | OWNER | MEMBER | VIEWER | Not a member |
| --- | --- | --- | --- | --- |
| Read the project, its tasks and labels, and the member list | Yes | Yes | Yes | 404 |
| Create, update, or delete tasks; attach or detach task labels | Yes | Yes | 403 | 404 |
| Create, update, or delete labels | Yes | Yes | 403 | 404 |
| Update or archive the project | Yes | 403 | 403 | 404 |
| Add members, change roles, remove other members | Yes | 403 | 403 | 404 |
| Leave the project (remove your own membership) | Yes, unless last owner | Yes | Yes | 404 |

Any authenticated account can create a project. The creator becomes its OWNER
in the same transaction, using the account from the session. Clients never send
an owner or creator field.

## Permissions

Code checks permissions, never role names. Each endpoint requires one
permission, and each role is a fixed set of permissions:

| Permission | Covers | OWNER | MEMBER | VIEWER |
| --- | --- | --- | --- | --- |
| PROJECT_READ | Read the project, tasks, labels, and members | Yes | Yes | Yes |
| TASK_WRITE | Create, update, or delete tasks; attach or detach task labels | Yes | Yes | No |
| LABEL_WRITE | Create, update, or delete labels | Yes | Yes | No |
| PROJECT_MANAGE | Update or archive the project | Yes | No | No |
| MEMBERS_MANAGE | Add members, change roles, remove other members | Yes | No | No |

- Leaving a project needs no permission beyond membership.
- The last-owner rule protects OWNER memberships; it is not tied to a permission.
- In this issue, the role-to-permission mapping is fixed in code. Custom project
  roles are planned in #42. They will store that mapping per
  project without changing any endpoint or check.
- Permissions are internal and do not appear in API responses yet. The API
  exposes only the caller's `role`.

## Error order

Checks run in this order, and the first one that fails sets the response:

1. 401: no valid session (existing authentication behavior).
2. 403: missing or invalid CSRF token on a write (existing behavior).
3. 400: malformed path or body.
4. 404: the project does not exist, or the caller is not a member. These two
   cases are deliberately the same response, so the API never reveals that a
   project exists.
5. 403: the caller is a member, but their role does not allow the action.
6. 404 or 409: resource state. Examples are a missing task (404), an archived
   project (409), and a last-owner violation (409).

Because permission comes before state, a VIEWER who writes to a task in an
archived project receives 403, not 409.

All errors use the existing application/problem+json shape.

## Changes to existing endpoints

- `GET /projects` returns only projects where the caller is a member. Its
  `active` filter and unpaginated list shape are unchanged.
- Every endpoint under `/projects/{projectId}`, including tasks and labels,
  applies the permission table above.
- Project responses gain `role`, the caller's role in that project. It lets a
  client hide actions the caller cannot perform; the server still enforces
  every rule.
- Project responses gain `createdByAccountId`. It is the creator's account ID
  and never changes, even if the creator later leaves.
- The rules for archived projects, inactive tasks, and task ownership are
  unchanged.

## Membership endpoints

| Method/path | Who | Input | Success | Expected errors |
| --- | --- | --- | --- | --- |
| GET /projects/{projectId}/members | Any member | page, size | 200 paged members | 400 invalid paging, 404 |
| GET /projects/{projectId}/members/{accountId} | Any member | None | 200 member | 400 invalid ID, 404 project or membership |
| POST /projects/{projectId}/members | OWNER | email, role | 201 member; Location header | 400 invalid input, 403 role, 404, 409 already a member, 422 account not found or disabled |
| PUT /projects/{projectId}/members/{accountId} | OWNER | role | 200 member | 400 invalid input, 403 role, 404 project or membership, 409 last owner |
| DELETE /projects/{projectId}/members/{accountId} | OWNER for anyone; others only their own accountId | None | 204 | 403 role, 404 project or membership, 409 last owner |

Member response:

```json
{
  "accountId": 3,
  "displayName": "Ana Souza",
  "email": "ana@example.com",
  "role": "MEMBER",
  "joinedAt": "2026-11-18T19:00:00Z",
  "updatedAt": "2026-11-18T19:00:00Z"
}
```

- `displayName` and `email` come from the identity module and are returned as
  separate fields. Formatting them for display is the client's job.
- Every member of a project, including VIEWERs, can see the email addresses of
  that project's other members. Non-members see nothing.
- The list uses the existing paged envelope: `items`, `page`, `size`,
  `totalElements`, `totalPages`. The same page and size limits as the task list
  apply.
- The list is ordered by role (OWNER, MEMBER, VIEWER), then by accountId.
- Membership changes do not create or change accounts.

Request rules:

- `email` follows the registration rules: it is trimmed, lowercased, and must be a
  valid address of at most 254 characters.
- `role` must be exactly OWNER, MEMBER, or VIEWER.
- Adding a member targets an existing active account. Invitations by email are
  out of scope.
- Adding an account that is already a member returns 409. It never changes the
  existing role; use PUT for that.
- An unknown or disabled email returns 422 instead of 404, so that 404 keeps a
  single meaning: the project was not found. Only OWNERs can reach this
  response, so the limited account lookup it allows is accepted.
- Setting the role a member already has returns 200 and changes nothing.

## Last-owner rule

A project must keep at least one active OWNER. Any change that would leave
none returns 409, whether it is a demotion, a removal, or an owner leaving.

Membership writes lock the project row before checking the rule. This is the
same lock that task writes and project archival already take. Two concurrent
requests therefore cannot each remove one of the last two owners.

An owner whose account is disabled does not count as an active owner.

## Archived projects

Archiving keeps its current meaning: task and label writes return 409, and
updating the archived project itself keeps returning 404, as before this issue.
Membership changes stay allowed in archived projects:

- an OWNER can still add members, change roles, and remove members;
- any member can still leave.

This lets owners give someone read access to an archived project's history.

## Disabled accounts

Identity already rejects every request from a disabled account (401), so a
disabled member cannot use any membership. Their membership rows are kept, and
they appear in member lists so owners can remove them. They cannot be added
again while disabled (422).

Known limitation: if every OWNER of a project is disabled, nobody can manage
that project, and there is no system administrator to recover it. No API can
disable accounts today, so this cannot happen yet. Recovery is left to a future
administration feature.

## Data model

V8 makes the following changes:

- Add `projects.created_by_account_id`. It is NOT NULL and references `accounts(id)`.
- Create `project_memberships` with these columns:
  - `id`
  - `project_id`, which references `projects`
  - `account_id`, which references `accounts`
  - `role`, limited by a CHECK to the three roles
  - `created_at` and `updated_at`
- Make (project_id, account_id) unique, and index account_id for "my projects"
  queries.
- Projects and accounts are never physically deleted by the application.
  `project_memberships.project_id` uses ON DELETE CASCADE so memberships cannot
  outlive a project removed manually or by test cleanup. The account foreign
  keys do not cascade: an account with memberships cannot be deleted.

Existing data: the project is not in production, and no existing project has a
recorded creator. V8 therefore requires empty `projects`, `tasks`, `labels`, and
`task_labels` tables, and fails with a clear error otherwise. This follows the
precedent set by V4. The migration never deletes data. A local development
database is reset by hand once, before V8 runs. CI and the tests always start
from an empty schema.

## Module boundaries

- `workmanagement` owns memberships, the permission checks, and the new table.
  It stores account IDs as plain values and never imports identity's domain or
  persistence classes.
- `shared` gains a small type for the current caller, for example
  `CurrentAccount(Long id)`. Identity fills it in from the session for each
  request. Controllers receive it as a parameter and pass it to services.
- Identity exposes a read-only application service, for example
  `AccountDirectory`. It finds an active account by email and returns
  `{id, displayName, email, active}` for a batch of IDs in one query.
- ArchitectureTests adds this rule: workmanagement may depend on
  `identity.application` services and DTOs, never on identity's domain,
  persistence, or security packages.

## Known limitation: global project keys

Project keys are unique across the whole system. A user who creates a project with
a key another team already uses receives 409, which reveals that the key exists.
This is accepted for now. Scoping keys to a future organization is out of scope.

## Out of scope

The following are not part of this issue:

- invitations and email delivery;
- organization or system administration;
- custom roles, which are planned in #42 (see Permissions);
- transferring ownership as a separate operation (use add-as-OWNER, then leave);
- frontend controls.

Task assignment (#10), comments (#11), and activity history (#12) build on this
contract.

## Delivery

The issue was first planned as three pull requests (foundation, enforcement,
member API). It was delivered as one pull request that closes #9, built in that
order:

1. **Foundation.** V8, the current-account plumbing, creator-becomes-OWNER on
   create, and the new response fields.
2. **Enforcement.** `ProjectAccess` applies permission checks to every existing
   project, task, and label endpoint; `GET /projects` lists only the caller's
   projects.
3. **Membership API.** The membership endpoints, the identity `AccountDirectory`
   service they use, the last-owner rule, and leaving a project. A single-member
   `GET` was added so the `Location` header returned by `POST` resolves.

## Verification

- A matrix test runs every project, task, and label endpoint as OWNER, MEMBER,
  VIEWER, and non-member, and checks that refused requests change nothing.
  Unauthenticated requests are covered by the existing authentication tests.
- `GET /projects` lists contain only the caller's projects, with their role.
- Error order: malformed input returns 400 before any access check; a VIEWER
  writing to an archived project receives 403, not 409; a hidden project and a
  missing project return identical 404 responses.
- Member API tests cover adding by email (normalized), duplicates (409),
  unknown or disabled accounts (422), owner-only management (403), non-members
  (404), immediate access changes, leaving, archived projects, and disabled
  owners not counting toward the last-owner rule.
- Concurrency tests: two owners leaving at once leaves exactly one owner (204
  and 409); two owners removing each other leaves exactly one owner (204, and 404
  for the request whose caller was removed first).
- A failed OWNER-membership insert rolls back the project creation.
- A migration test confirms that V8 rejects non-empty tables and applies cleanly
  to an empty database.
- ArchitectureTests enforces the module rule.
- The Postman collection has an `08 Memberships` folder, including requests made
  as a second account. Existing flows keep passing, because the account that
  creates each project becomes its OWNER.
