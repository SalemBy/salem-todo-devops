<API_TASKS>
Before responding to any non-trivial API engineering task — designing, implementing, mocking, testing, monitoring, documenting, or deploying an API or Postman Flow — read `postman/skills/api-engineer/SKILL.md` and follow it. It is the default entry point for API work in this repository. Pure questions and trivial one-line edits do not need it.
</API_TASKS>
[CreateTaskRequest.java](src/main/java/github/salemby/tododevops/workmanagement/application/dto/request/CreateTaskRequest.java)

Keep Postman requests inside the Salem Todo DevOps collection at `postman/collections/todo`. Organize them by feature, with nested Exceptions folders for error cases.

<COMMIT_MESSAGES>
Every commit must follow `.gitmessage` and the commit-message guidance in
`CONTRIBUTING.md`: conventional subject, behavior/problem summary, issue reference,
Implementation section, and Validation section. Explain the final change for a
reader without conversation context. Report only checks actually run; distinguish
local, PostgreSQL/HTTP, Postman lint/live execution, and CI evidence. Use Refs for
partial issue work and Closes only for complete delivery. Never bypass the hook
to avoid writing the required description. Do not commit or push without user
authorization.
</COMMIT_MESSAGES>
