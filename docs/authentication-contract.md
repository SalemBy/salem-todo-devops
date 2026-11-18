# Authentication contract (issue #8)

Account persistence, validation, password hashing, endpoints, and session protection
are implemented locally on the issue branch; review and CI remain before delivery.
The user selected browser/Postman clients and server-side sessions.

## Identity and registration

Add an identity module following the existing api/application/domain/infrastructure
boundaries. A user has an immutable generated ID, email, displayName, passwordHash,
active flag, creation/update timestamps, and a credential version for revocation.
Never serialize the entity or include password hashes in response DTOs.

Normalize email by trimming surrounding whitespace and lowercasing with Locale.ROOT.
This application treats the complete email address as case-insensitive. Enforce
uniqueness on the normalized value in PostgreSQL, including disabled accounts.
Require a valid ASCII email up to 254 characters; displayName must contain visible text,
reject NUL, and contain at most 100 UTF-16 code units. No email ownership verification is
implied by registration; email delivery is outside this issue.

Password policy: 15-128 Unicode code points, no silent trimming or
normalization, reject NUL. Use Spring Security PBKDF2-HMAC-SHA256 with 600,000
iterations, random 16-byte salts, and version identifier `pbkdf2-sha256-600k-v1`.
This uses the JDK provider without an additional cryptographic provider dependency.
Validate production latency before release; future parameter changes require a new
identifier and a migration-on-login policy. Never use raw password comparison
or log authentication request bodies.

Registration is controlled by configuration and disabled by default. Explicitly
enable it during local development or controlled onboarding. There are no seeded
users, default passwords, or automatic administrator privileges for the first user.
Existing business records are retained without assigning arbitrary ownership.

## Endpoints

| Method/path | Input | Success | Expected errors |
| --- | --- | --- | --- |
| GET /auth/csrf | None | 200 with token and headerName; creates anonymous session if needed | Standard server errors |
| POST /auth/register | email, displayName, password | 201 with user DTO; does not log in | 400 invalid input, 409 duplicate email, 403 registration disabled |
| POST /auth/login | email, password | 200 with user DTO and rotated session cookie | 400 malformed input, generic 401 invalid credentials/disabled account |
| GET /auth/me | Session cookie | 200 with current user DTO | 401 absent/expired/revoked session |
| POST /auth/logout | Session cookie and CSRF header | 204; clears current session/cookie | 401 absent/expired/revoked session, 403 invalid CSRF |
| POST /auth/password | currentPassword, newPassword | 204; revokes all sessions including current | 400 invalid input/current password, 401 invalid session, 403 invalid CSRF |

The user DTO contains only id, email, displayName, active, createdAt, and updatedAt.
Login and currentPassword accept 1-256 UTF-16 code units, rejecting NUL; newPassword
uses the registration password policy. Login email follows registration normalization
and format/length validation. Invalid input returns 400 before credential checking.
Login never distinguishes nonexistent email, wrong password, or disabled account.
Password change requires the current password and uses a transactional credential
version increment; after success the client must log in again. Concurrent changes
must not lose a revocation update. A request already executing before revocation
may finish; subsequent authenticated requests must reject the old credential version.

## Session and CSRF lifecycle

Use Spring Security mechanisms for authentication, session fixation protection,
SecurityContext persistence, and logout. Initial deployment is a single server with
in-memory sessions: restart signs users out. Expiry is 30 minutes idle
and 12 hours absolute. No remember-me or persistent login cookie in this version.

Use an HttpOnly, SameSite=Lax, host-only session cookie with path /. Secure is
required in deployed HTTPS environments; allow an explicit local HTTP development
configuration. Do not put session identifiers in URLs, JSON, or browser localStorage.
No cross-origin credentialed CORS policy is introduced without a concrete client origin.

Clients call GET /auth/csrf and retain its token in memory. Include the indicated
header and the session cookie on every state-changing request, including register,
login, logout, and password changes. Obtain a fresh CSRF token after login/logout
or expiry; do not cache the endpoint response. Postman uses its cookie jar and a
temporary variable for the CSRF token, never committed live credentials.

CSRF rejection returns 403. For protected business routes, authentication must
produce 401 for an absent, expired, disabled, or revoked identity before processing
the business operation and before CSRF checks. An
authenticated request with missing/invalid CSRF returns 403. Public authentication
writes still require CSRF and return 403 when it is absent or invalid.

Check account active state and credential version on authenticated requests so
deactivation/password changes revoke existing sessions, not only future logins.
There is no public account-deactivation endpoint in this issue.

## Protected routes and errors

Public allowlist: GET / (existing minimal status), GET /auth/csrf,
POST /auth/register (subject to configuration), and POST /auth/login.
All other application routes require authentication by default. Internal error
dispatch must preserve the original ProblemDetail status without making business
routes public. Disable generated HTML login pages and browser redirects.

Security entry-point, access-denied, CSRF, and login handlers return the existing
application/problem+json shape: type, title, status, detail, instance, and errors.
Passwords, hashes, session IDs, and CSRF tokens must not appear in error details.
Do not add registration tokens or shared bootstrap secrets to source control.

This establishes identity only. Authenticated users retain current project/task
access until issue #9 adds membership permissions. Do not claim tenant isolation
or expose this intermediate behavior as project authorization.

## Verification before delivery

Use disposable PostgreSQL and test-created accounts. Adapt existing HTTP and
Postman workflows to obtain sessions and CSRF tokens; keep production security
enabled. Test uniqueness races, password hashes, generic login errors, session
rotation, expiry, logout, password-change revocation across two clients, disabled
accounts, CSRF, consistent 401/403, and existing business regressions. Verify fresh
and upgrade migrations preserve task/project/label data. No applied migrations
are edited. Update README with setup, onboarding, HTTPS/local configuration,
session limits, and the explicit boundary with issue #9.

## Password encoding references

- [Spring PBKDF2 encoder](https://docs.spring.io/spring-security/reference/7.0/api/java/org/springframework/security/crypto/password/Pbkdf2PasswordEncoder.html)
- [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
