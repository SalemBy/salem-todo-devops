# Password Hashing Outside Transactions Contract (#44)

## Problem

Passwords are hashed with PBKDF2-SHA256 at 600,000 iterations, which takes a few hundred milliseconds of CPU per hash. Today that work runs inside database transactions:

- `AuthenticationService.authenticate` is `@Transactional(readOnly = true)`, so every login holds a pooled connection for the whole hash. This includes unknown emails, which are hashed against a dummy hash.
- `AuthenticationService.changePassword` is `@Transactional` and loads the account with `SELECT ... FOR UPDATE`. It then hashes twice (checking the current password, encoding the new one) while holding both a connection and the account row lock.

The pool has 10 connections by default, and every authenticated request needs one in `AccountSessionFilter`. So about ten logins at the same moment make every other request wait, and the waiting requests fail with 500 once Hikari's 30-second connection timeout passes.

## Decision

No password hash runs while the application holds a database connection or a row lock.

### Login (`POST /auth/login`)

1. Read the account by normalized email in the repository's own short read-only transaction. The connection is released when the read returns.
2. Run `passwords.matches(...)` with no transaction open: against the account's hash, or against the dummy hash when the email is unknown.
3. Return the same principal, or the same `BadCredentialsException`, as today.

`authenticate` is no longer `@Transactional`. Its signature and validation stay the same.

### Password change (`POST /auth/password`)

**Unlocked phase** (no transaction around the hashing):

1. Read the account by ID in the repository's own short transaction. If it is missing, inactive, or its credential version differs from the session's, return **401**.
2. Check the current password with `passwords.matches`. If it is wrong, return **400** and change nothing.
3. Encode the new password with `passwords.encode`.

**Locked phase** (one short transaction, through `TransactionOperations`):

4. Lock the row with `findForPasswordChange` (`SELECT ... FOR UPDATE`).
5. Recheck that the account is active and its `credential_version` still equals the session's. If not, return **401** and discard the hash from step 3.
6. Store the new hash, increment `credential_version` by exactly 1, flush, and commit.

Only steps 4–6 hold the row lock. No hashing happens inside them.

## Unchanged behavior

- No endpoint, status code, response body, header or Postman request changes.
- `POST /auth/password` keeps its error order: **401** (session account inactive or credential version changed) → **400** (current password incorrect) → **204**.
- Login failures stay generic: a wrong password, an unknown email and a disabled account return the same 401 body.
- An unknown email still costs a full hash, so timing does not reveal which accounts exist.
- A successful password change increments `credential_version` by exactly 1 and ends every session of that account, including the caller's.

## Concurrency

- **Two concurrent changes from sessions of the same account:** both may pass the unlocked phase. The first to take the lock commits (204, version becomes 1). The second finds the version changed and gets **401**, and its precomputed hash is discarded. The winner's new password is the one that works. This keeps the guarantee tested by `concurrentPasswordChangesCannotLoseRevocationOrOverwriteWinner`.
- **Account disabled during a change:** if it happens after the unlocked phase, the locked recheck sees `active = false` and returns **401**. The password hash and the version stay unchanged.
- **Password changed elsewhere between the phases:** this always increments `credential_version`, so comparing the version is enough. The stored hash is not compared.

## Out of scope

- Rate limiting public authentication endpoints (#45).
- Connection pool sizing and PBKDF2 parameters.
- Removing the duplicated session-validity checks in `isValid` and `current` (#46).

## Verification

- A new `PostgresPasswordHashingIT` uses a test-only `GatedPasswordEncoder`, which pauses inside a hash on command. While a login (for both a known and an unknown email) or a password change is paused mid-hash, the IT asserts that the pool has **0 active connections**. On the code before this fix the count is 1, so the test fails there.
- The same IT covers an account disabled in the middle of a password change: 401, with the hash and version unchanged.
- `PostgresAuthenticationIT` passes unchanged, including the concurrent password-change test.
- `.\mvnw.cmd -B --no-transfer-progress verify` and `.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it verify` pass.
- A live Postman run of `00 Authentication` passes with no request changes.
- A fresh adversarial review of the whole branch runs before the pull request.
