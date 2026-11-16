# Test-suite review — issue #28

Branch: `chore/28-high-impact-tests`, based on master `80fb295`.
No public API, production source or migration behavior changed.

## Decision

Keep tests that protect externally visible behavior, rejected-write integrity,
module boundaries, validation activation or production database guarantees.
Remove duplicate execution rather than removing meaningful assertions to lower a count.

H2 adds no unique requirement: all four H2 wrappers inherit exactly the same
contracts as PostgreSQL. Remove its dependency, profile properties and wrappers.
Convert the four abstract contracts directly into concrete PostgreSQL IT classes,
removing the now-unnecessary inheritance layer. PostgreSQL remains the only database
used for HTTP, SQL constraints, transactions and migrations.

## Inventory and behavior mapping

Times are JUnit class elapsed seconds from one before/after run; context startup
is charged to the first class using it. They are not isolated microbenchmarks.

| Group | Decision and protected behavior | Before -> after seconds | Database |
| --- | --- | --- | --- |
| ArchitectureTests | Keep dependency boundaries between shared, API, application, domain and infrastructure | 1.702 -> 1.857 | None |
| ProjectServiceTests | Keep localized PostgreSQL duplicate-key translation and propagation of unrelated integrity failures | 0.434 -> 1.487 | Mock repository; actual PostgreSQL exception type |
| ServiceValidationTests | Consolidate 33 executions into 3 focused Spring-proxy checks: create/update cascading validation, null requests, no repository access on rejection | 1.284 -> 1.416 | H2 -> none |
| ValidationStartupTests | Keep real validator availability and fail-closed startup without a provider; the latter runs in an isolated JVM | 1.339 -> 1.325 | None |
| Project API | Keep CRUD, immutable fields, length/key rules, missing IDs, filters, archive idempotency, archived-key reservation and concurrent duplicate creation | H2 12.770 + PG 8.880 -> PG 8.805 | PostgreSQL |
| Project-task API | Keep project isolation, rejected cross-project writes without mutation, removed global routes, malformed/fractional IDs, immutable ownership and archived-project write rejection/history | H2 1.031 + PG 1.115 -> PG 0.849 | PostgreSQL |
| Required-field HTTP | Keep omitted/null/empty/whitespace/Unicode-invisible values on create/update; field errors, row counts and preserved update timestamps (#22) | H2 0.407 + PG 0.471 -> PG 0.380 | PostgreSQL |
| Task API | Keep HTTP CRUD, status, length rejection, soft deletion/filtering, protocol errors, sanitized unexpected failures and service-validation error mapping | H2 1.843 + PG 1.384 -> PG 1.538 | PostgreSQL |
| PostgresMigrationIT | Keep V1/V2-to-V3 data preservation, active defaults, not-null/check constraints and uniqueness even for archived keys | 1.328 -> 1.368 | PostgreSQL, random schema |
| PostgresAssociationMigrationIT | Keep V4 refusal for populated legacy tasks without mutation; empty-table upgrade, ownership NOT NULL/FK, deletion restriction and index | 2.202 -> 2.353 | PostgreSQL, random schema |
| Postman collection | Keep deployed consumer workflows/examples and essential exception contracts; optional validation dataset is diagnostic, not another mandatory CI matrix | Not rerun: no requests changed | Deployed disposable API |

Removed `persistsTask`: generic repository save/read smoke coverage duplicates
HTTP creation and migration assertions. Creation timestamps remain protected by
update/soft-delete tests and migration preservation checks.

The service tests still exercise actual Spring validation proxies, not manually
invoked validators. Repository mocks let them prove rejection before any persistence
access without starting JPA or a database. The exhaustive input matrix stays at
the HTTP boundary, where omitted JSON properties, field errors and non-mutation matter.

Keep status and validator-startup smoke checks: these are small explicit public
health and fail-closed configuration contracts. Keep independent HTTP methods and
error cases when they exercise distinct routing, binding or persistence paths.

## Before/after evidence

Same machine, Java 17.0.15, disposable PostgreSQL 18 on loopback port 55438,
same cached Maven dependencies, same command:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.maven-cache' -B --no-transfer-progress -Ppostgres-it clean verify
```

| Measure | Before | After |
| --- | ---: | ---: |
| Surefire executions | 63 | 8 |
| Failsafe executions | 29 | 28 |
| Total | 92 | 36 |
| Failures/errors/skips | 0/0/0 | 0/0/0 |
| Maven elapsed | 52.787 s | 36.046 s |
| Full Spring Boot test contexts | 5 | 2 |
| Database engines | 2 | 1 |

Observed full-build reduction: 16.741 seconds (31.7%). Single warm-cache runs do
not establish a stable performance benchmark. The goal is meaningful protection,
not a count or coverage percentage. Individual mocked-test times are not necessarily
lower because Mockito initialization moved into the smaller fast suite.

Mutation check: temporarily removed `@Validated` from TaskService. All three
ServiceValidationTests failed (expected ConstraintViolationException, received
ResponseStatusException; repository-access assertions also detected the bypass).
Restored the exact original source in a finally block. No mutation is committed.
The restored fast suite is run with all TEST_DB_* variables absent.

Local raw evidence (ignored): `.local-tools/issue28-before.log`,
`issue28-after.log`, `issue28-mutation.log`, `issue28-fast.log` and baseline XML
report copies. These are local artifacts, not links to published CI evidence.

## Known bugs and limits

- #24: retain soft-delete, inactive-update and ownership regression coverage;
  deterministic delete/update concurrency coverage remains work for that issue.
  Sequential tests do not prove race safety.
- #25: archive/update concurrency remains a known open bug; this cleanup neither
  fixes it nor claims the retained archive tests detect that race.
- #26: project IDs now come from URLs; fractional-ID rejection is retained in
  PostgresProjectTaskIT. The old body-coercion reproduction predates this contract.
- #27: embedded NUL rejection remains an open bug without a dedicated passing
  regression. Required-visible-text tests must not be presented as NUL protection.

No tests were disabled to conceal those gaps. Dedicated concurrency/input fixes
remain separate; this review does not close #24–#27.

## Execution and CI

Fast local feedback, with no database or credentials:

```powershell
.\mvnw.cmd -B --no-transfer-progress test
```

Complete backend verification:

```powershell
.\mvnw.cmd -B --no-transfer-progress -Ppostgres-it verify
```

Set TEST_DB_URL, TEST_DB_USERNAME and TEST_DB_PASSWORD to a disposable
PostgreSQL 18 database, as documented in README. HTTP fixtures are removed after
tests and use a random schema per cached context; migration fixtures use random
schemas with finally cleanup. Tests sharing a context run sequentially. Independent
Maven processes use different schemas. Never use valuable application data. Missing configuration
or an unavailable database fails the required integration run; there are no
assumptions or disabled tests that silently skip it.

The existing GitHub Build and test job already provisions disposable PostgreSQL 18,
runs postgres-it verify, and uploads Surefire/Failsafe reports even on failure.
No workflow YAML change is necessary. Default verify alone is not complete backend
verification. README, CONTRIBUTING and the Postman guide now describe this split.

Postman requests remain under postman/collections/todo in feature folders with
nested Exceptions folders. Intentional overlap checks the deployed consumer
experience; Java additionally inspects persisted state and migration behavior.
No Postman requests or assertions were removed. Existing local changes to the
workspace binding and collection description are excluded from this change.

## Reliability follow-up (16 November 2026)

These changes continue on issue #28's existing branch after the published PR
baseline. They were initially kept local for review; publication was subsequently
authorized after successful Postman verification.
The earlier timing table records the initial simplification, not the following
parameterized execution count.

1. **Baseline:** a fresh `-Ppostgres-it clean verify` passed 36 executions in
   39.202 seconds before the follow-up edits.
2. **Bounded HTTP:** TestHttp reuses one Java HTTP client with a five-second
   connection timeout and a fifteen-second request timeout. Every HTTP request
   in the four integration suites uses it. No new test dependency was added.
3. **Controlled unique-key conflict:** replace the simultaneous-client-start test
   with an uncommitted PostgreSQL insert competing with an actual HTTP POST.
   Observe `pg_blocking_pids` before committing the winning insert; then require
   HTTP 409, the standardized error body and exactly one unchanged winning row.
   JDBC deliberately controls the winning transaction. Ordinary HTTP 201 and
   sequential duplicate rejection remain covered separately. Lock observation,
   SQL queries, HTTP calls, worker completion and cleanup have bounded waits.
4. **Schema isolation:** each cached Spring HTTP context creates a generated
   `http_test_<uuid>` schema. Flyway, Hibernate and Hikari use that schema.
   A disposable callback registered before application beans drops it after
   those beans are destroyed, on normal shutdown or refresh failure. Per-case
   fixture cleanup remains, and required-field cleanup is one ordered method.
   Migration tests retain their independently generated schemas. Tests within a
   context remain sequential; parallel methods in one context are not supported.
5. **Assertions and diagnosis:** use Jackson 3 `stringValue()` for expected JSON
   strings instead of deprecated `asText()`. Remove the exact response-field count
   while still asserting required fields, values and initial timestamps. Backdate
   lifecycle fixtures so timestamp-advancement assertions detect missing updates
   without relying on clock granularity. Keep immutable-field assertions, with a
   spoofed timestamp different from the fixture. Name individual create/update,
   key-format and required-field validation cases. Rejected task updates now compare
   complete before/after HTTP records, including status and timestamps. Equivalent
   one-space and three-space required-field cases were consolidated.
6. **Verification:** clean verification with compiler deprecation reporting enabled
   passed **96 executions: 8 fast + 88 PostgreSQL**, with zero failures, errors or
   skips and no compiler deprecation warnings, in **35.908 seconds**. This higher
   count reports the existing input matrix as independent invocations; it is not
   additional duplicated database coverage. Single local timings are not a stable
   performance benchmark.

The controlled-conflict mutation temporarily disabled only ProjectService's
duplicate-key exception translation. The selected test reached the database lock,
then failed with **expected 409, received 500**. The exact original production
source was restored in a finally block; no production change remains.
After restoration and the final immutable-timestamp fixture adjustment, a targeted
rerun of the controlled conflict and project CRUD tests passed both executions
in 22.154 seconds. This rerun is in addition to the complete 96-execution run.

Isolation evidence: a `public.projects` sentinel using the same `TODO` key as an
HTTP fixture remained byte-for-byte identical after the full suite. No
`http_test_*` schemas remained after either the mutation failure or the successful
full run. This also demonstrates that the suite need not use an empty public
schema. A forcibly killed JVM cannot guarantee callback cleanup; continue using
a disposable database. Concurrent independent Maven processes were not separately
exercised in this follow-up.

Raw local evidence (ignored): `issue28-reliability-baseline.log`,
`issue28-conflict-mutation.log`, `issue28-reliability-final.log`,
`issue28-reliability-repeat.log` and `issue28-sentinel-before/after.txt` in
`.local-tools/`. Postman request files and production behavior are unchanged.
Bugs #24–#27 remain separate.

### Packaged-application Postman verification

The restored packaged application was started on `http://127.0.0.1:18088`, using
a new disposable PostgreSQL database (`issue28_postman_validation`) and production
Flyway migrations. Both commands exited with code 0:

```powershell
postman collection run postman/collections/todo --env-var baseUrl=http://127.0.0.1:18088 --timeout-request 15000 --timeout 120000 --no-report-events
postman collection run postman/collections/todo -i '04 Validation' -d postman/documents/required-fields-cases.json --env-var baseUrl=http://127.0.0.1:18088 --timeout-request 15000 --timeout 120000 --no-report-events
```

- Full collection: 68 requests, 68 assertions, zero failures (about 3 seconds).
- Nine-case validation dataset: 36 requests, 36 assertions, zero failures
  (about 1.2 seconds).
- Evidence is against the actual packaged API and PostgreSQL, not a mock.
- Local logs: `.local-tools/issue28-postman-full.log` and
  `.local-tools/issue28-postman-dataset.log`. Run events were not published.
- The test application and disposable database cluster are stopped after use.

The subsequent GitHub Build and test check on PR #30 verifies the pushed Java
suite separately. Postman CLI suggested a version/skills update; it did not block
either run and no tooling update was required for this verification.
