# Salem Todo DevOps — Postman guide

One collection contains all requests: `postman/collections/todo`.
Open this repository in Postman's Native Git workflow to use these same files in
the desktop app. Expand **Salem Todo DevOps** and follow the numbered folders.

## Start here

1. Start the API against a disposable development or test database.
2. Set the collection variable `baseUrl` (default: `http://127.0.0.1:8080`).
3. Send **01 Status → Application status**. Expect HTTP 200 and `status: running`.
4. Enable `AUTH_REGISTRATION_ENABLED=true` and, for local HTTP only,
   `SESSION_COOKIE_SECURE=false` on the test server.
5. In Collection Runner, select **00 Authentication** followed by the desired
   feature folders in the same run. Leave the cookie jar enabled. Authentication
   generates temporary test credentials and CSRF values in run-local variables;
   do not export cookies, run logs containing credentials, or tokens to Git.

The authentication folder covers registration, login, current account, password
change, logout, duplicate email, invalid input, and CSRF errors. It finishes with
a fresh login and CSRF token so later business requests inherit the required
header. Run this setup again after expiration or server restart.

Every write obtains a fresh CSRF token automatically using the cookie jar before
sending the request. This also refreshes the token after login, logout, and server
restart. Explicit token requests remain available for learning and inspection.
An expired login still requires signing in again.

In manual mode, **Get CSRF token** preserves your existing authEmail/authPassword.
To register a different account, set both values in the selected environment or
clear both before sending this setup request. Re-registering the same email returns
409. Use **Login** when the account already exists. Generated credentials are for
disposable testing, and password-change scenarios change that test account's password.

To use the desktop **Send** button one request at a time, select a private local
Postman environment and set its text variable `manualAuth` to `true`. Send **Get
CSRF token**, then **Register account**, using the same host and cookie jar.
This mode keeps generated test credentials and CSRF values in that environment
between sends. Keep the values local and clear them after testing. A 403 with
`Request forbidden` means the token or its matching session cookie was rejected.
A 403 with `Registration is disabled` means the server requires the onboarding
setting in step 4. Restart the local server after changing its configuration.

Setup requests create records and capture their IDs automatically. Leave generated
ID, key and snapshot variables empty initially; their descriptions explain their
purpose. Run setup again when rerunning a feature or after resetting the database.
Individual requests depend on earlier steps in their feature.

## Collection layout

```text
Salem Todo DevOps
├── 00 Authentication
├── 01 Status
├── 02 Projects
│   ├── 01 Create and read
│   ├── 02 Update
│   ├── 03 Exceptions
│   ├── 04 Archive and filters
│   └── 05 Archive exceptions
├── 03 Tasks
│   ├── 01 Setup
│   ├── 02 Create and read
│   ├── 03 Update
│   ├── 04 Exceptions
│   ├── 05 Project backlogs
│   ├── 06 Soft delete and filters
│   ├── 07 Inactive task exceptions
│   ├── 08 Archive owning project
│   ├── 09 Archived project exceptions
│   └── 10 Read retained history
├── 04 Validation
│   ├── 01 Setup
│   └── 02 Exceptions
├── 05 Task metadata
├── 06 Labels
├── 07 Task filters
└── 08 Memberships
    ├── 01 Setup
    ├── 02 Manage members
    ├── 03 Exceptions
    └── 04 Access as member
```

**Memberships** registers a second disposable account (registering does not change
the current login), adds it to a project, and checks the owner-only and last-owner
rules. Its last subfolder logs in as that account to verify 403 and 404 responses
and leaving the project, then logs back in as the original account.

**Projects**, **Tasks** and **Validation** each have their own setup and can run
independently. The task scenario creates two projects to verify that ownership
cannot move and that backlogs remain separate. The project scenario uses its own
projectId; task scenarios use taskProjectId and otherProjectId.

Exceptions folders intentionally produce error responses. A 400, 403, 404, 409 or
422 is a passing test when it matches the expected contract. Check the assertion results,
not only the HTTP status color in Postman.

## Run from the CLI

```powershell
# Full collection, including authentication setup
postman collection run postman/collections/todo

# Independent project or task scenario
postman collection run postman/collections/todo -i "00 Authentication" -i "02 Projects"
postman collection run postman/collections/todo -i "00 Authentication" -i "03 Tasks"

# Override the address for an isolated test instance
postman collection run postman/collections/todo --env-var baseUrl=http://127.0.0.1:18080

# Nine required-field cases: 36 requests and 36 assertions
postman collection run postman/collections/todo -i "00 Authentication" -i "04 Validation" -d postman/documents/required-fields-cases.json
```

For the same dataset in the desktop app, select **04 Validation** in Collection
Runner and choose `postman/documents/required-fields-cases.json` as its data file.
Each iteration creates one valid owner and tests three invalid required fields.
Cases cover missing, null, empty, ordinary whitespace and invisible characters.
Without a data file, Validation tests whitespace once.

## Rules exercised

| Operation | Expected behavior |
| --- | --- |
| Create project | 201; unique uppercase key, required visible name |
| Update project | 200; name and description may change, key stays fixed |
| Archive project | 204; repeated archive preserves its timestamp |
| Create task | 201; requires an existing active project and visible title |
| Update task | 200; title, description and status may change, project stays fixed |
| Read backlogs | 200; project isolation and active=true/false/all filters |
| Soft delete task | 204; task remains readable with active=false |
| Invalid fields or filters | 400 with ProblemDetail and appropriate field errors |
| Unknown project or inactive task update | 404 |
| Duplicate project key or writes in archived project | 409 |

All task operations use /projects/{projectId}/tasks, with /{taskId} for individual tasks.
The URL supplies ownership; creation bodies contain title and optional description.
Global /tasks routes are unavailable (404). Cross-project reads, updates and deletes
return 404. Fractional project IDs return 400. Tests verify rejected writes preserve data.

An extra key in a project update or projectId in a task payload is ignored; tests
verify that identity and ownership remain unchanged. Project archival preserves
both active and inactive tasks. All writes in an archived project return 409;
updating an inactive task in an active project returns 404. Rejected updates are followed by reads comparing
the complete persisted record, including timestamps, to an earlier GET response.

No automatic system project is assumed or created. Scenarios leave their generated
records in the target database, some archived. Use disposable data. The collection
does not reset a database or delete unrelated user records.

All future requests belong in this collection, grouped by feature and with clear
Exceptions folders. Keep setup, mutation and verification steps ordered. The
`.postman/resources.yaml` workspace binding and local CLI login are separate from
the collection files; do not commit credentials.

## Java and Postman responsibilities

Java owns automated persistence, migration, concurrent uniqueness, validation wiring,
error sanitization and ownership regressions. Postman owns runnable consumer examples
and ordered project/task workflows, with essential error contracts. Their overlap
on ownership, rejection without mutation and lifecycle behavior is intentional:
Postman exercises a separately deployed application, while Java inspects stored data.
The nine-case validation dataset is an optional targeted diagnostic for validation
changes, not an additional mandatory copy of the Java matrix on every build.
See [the test review](../docs/test-suite-review.md) for decisions and known gaps.
