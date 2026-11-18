# Accounts and authentication: issue #8

Status: account foundation and authentication/session protection implemented locally.
Existing HTTP tests authenticate through real endpoints. Postman includes a session
workflow and inherited CSRF headers. Local implementation, review, and verification
are complete. GitHub CI and PR review remain before merge.
See authentication-contract.md for the contract.

## Scope and sequence

The client/session decision was recorded before implementation. The implementation
stages are:

1. Account foundation: add an identity module with immutable user IDs, normalized unique email,
   display name, password hash, active state, and timestamps. Use a new forward
   Flyway migration; preserve existing projects, tasks, and applied migrations.
2. Authentication: implement registration, login, current-user, logout, and password change with
   Spring Security. Use supported password encoders; never expose hashes or
   credentials through ordinary responses or logs.
3. Route protection: protect business routes by default. Return the existing ProblemDetail shape
   for authentication/filter-chain errors as well as controller errors. Use
   generic login failures and distinguish absent/invalid identity (401) from
   forbidden authenticated actions (403).
4. Client migration: migrate HTTP tests and Postman workflows to authenticate with test-created
   users. Keep production security enabled in tests; do not solve regressions
   by disabling the security filter chain.
5. Verification: verify against disposable PostgreSQL and a live isolated application, then
   review README, API contract, Postman scenarios, and migration compatibility.

## Initial client decision

Selected by the user: browser application plus Postman, using server-side sessions
with an HttpOnly cookie and CSRF protection. It must include session rotation at login,
bounded lifetime, explicit context persistence, and logout invalidation.

Bearer tokens and JWT issuance are outside this first version.

## Contract decisions recorded in authentication-contract.md

- Endpoint paths, request/response DTOs, input lengths, password rules, and errors.
- Email normalization and database uniqueness, including concurrent registration.
- Registration availability and bootstrap policy; no default shared credentials.
- Idle/absolute expiration and behavior across server restarts.
- Revocation of existing logins after password changes or account deactivation.
- Public routes, cookie/transport settings, and CSRF acquisition/refresh rules.
- Compatibility of the shared HTTP test client with multiple isolated users.

## Acceptance evidence

Final local verification on 2026-11-18:

- Maven `verify -Ppostgres-it` passed 135 tests: 11 unit and 124 PostgreSQL
  integration tests, with zero failures, errors, or skips. Includes real idle
  expiration, the absolute-expiry boundary, concurrent registration/password
  changes, session revocation, and existing business-route regressions.
- The complete Postman collection passed 141 requests and 141 assertions against
  the rebuilt application and a disposable PostgreSQL database (exit code 0).
- Postman collection lint passed: 141 requests, zero errors and zero warnings.
- `git diff --check` passed. Temporary verification databases and the live test
  server were cleaned up. No production database was used.
- These are local results, not GitHub CI evidence. Deployed HTTPS behavior and
  production password-hashing latency have not been measured in this environment.

Cover registration/login/current-user/logout/password-change success and failure;
duplicate normalized emails; disabled accounts; hash persistence without leakage;
expired/revoked credentials; consistent 401/403; rollback and migration preservation;
and existing project/task/label/filter behavior under authenticated requests.

## Boundaries

Project ownership, memberships, and OWNER/MEMBER/VIEWER permissions belong to #9.
Authentication alone must not be described as project-level authorization: until
#9 is implemented, authenticated users can access the existing business routes.
OAuth, password-reset emails, MFA, and frontend screens remain outside #8.
Search and configurable sorting remain outstanding scope from #7.

## Framework references

- https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html
- https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
