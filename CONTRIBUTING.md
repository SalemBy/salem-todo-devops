# Contributing

## Issue to merge

1. Open an issue using the feature request or bug report form. Define the expected
   behavior and acceptance criteria before implementing it. Blank issues are
   available for documentation, maintenance, and other work.
2. Create a branch from the issue's Development section, based on `master`, then
   fetch and check it out locally. Use names such as `feature/12-projects`,
   `fix/18-task-validation`, or `chore/20-repository-workflow`.
   If you create the branch locally instead, link the PR explicitly in step 5.
3. Implement a focused change. Add relevant tests for changed behavior, preserve
   existing Flyway migrations, and add a new migration for schema changes.
4. Run the build from the project root:

   ```powershell
   .\mvnw.cmd -B --no-transfer-progress verify
   ```

   On Linux or macOS, run `./mvnw -B --no-transfer-progress verify`.
   Java 17 or newer is required. Tests use H2 and do not need production credentials.
5. Commit and push your branch, then open a pull request targeting `master`.
   Complete the PR template. Put `Closes #12` in the description to close issue
   12 when the PR merges. Use `Refs #12` for partial work that must leave it open.
6. Wait for the `Build and test` check, inspect the diff and test results, and
   resolve review conversations. When working alone, perform this review yourself;
   another person's approval is not required by this project's workflow.
7. Merge only when acceptance criteria are satisfied and CI passes. Merging a
   linked PR into the default branch closes its issue. Opening a PR or naming a
   branch after an issue does not by itself close the issue.
8. GitHub deletes merged remote branches automatically. Switch back to `master`
   and pull before starting the next issue.

## Branch protection

The intended protection for `master` is:

- Require a pull request before merging, with zero mandatory approvals for solo work.
- Require the GitHub Actions `Build and test` check and an up-to-date branch.
- Require review conversations to be resolved.
- Block force pushes and branch deletion, and apply the rules to administrators.

At setup time, GitHub rejected branch protection for this private repository
because it requires an eligible paid plan. These rules are therefore a manual
convention until protection is enabled in repository Settings. CI still runs,
but cannot currently prevent a direct push or a merge with failed checks.

## Configuration and API changes

Copy `.env.example` to `.env` only if you do not already have local configuration.
Never commit `.env`, credentials, database files, or build output.
Update README and the local Postman collection when the API contract changes.
Postman requests can mutate data: use an isolated development or test database.
