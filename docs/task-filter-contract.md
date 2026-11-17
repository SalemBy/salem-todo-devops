# Project task filters (issue #7, stage 2)

`GET /projects/{projectId}/tasks` combines the following optional parameters
with AND. Omission leaves that dimension unrestricted; supplied empty values
are invalid. Existing active/page/size behavior and ascending ID order remain.

| Parameter | Contract |
| --- | --- |
| status | Exact PENDING, IN_PROGRESS, COMPLETED |
| type | Exact TASK, BUG, FEATURE, ENHANCEMENT, CHORE |
| priority | Exact LOW, MEDIUM, HIGH, CRITICAL |
| labelId | One positive integer label ID; includes historical archived associations |
| dueFrom | Inclusive lower date bound, yyyy-MM-dd, years 0001-9999 |
| dueTo | Inclusive upper date bound with the same format |

Invalid enums, malformed dates, invalid IDs, or dueFrom after dueTo return 400
through the existing ProblemDetail contract. A date bound excludes undated tasks.
An unknown or foreign label matches no tasks, returning empty items and zero
totals; it does not reveal label existence in another project. An unknown project
still returns 404. Archived projects remain readable; active filters apply to tasks.

The existing items/page/size/totalElements/totalPages envelope is preserved.
Counts use all supplied filters before pagination. Multiple labels on a task
must not duplicate items or inflate counts. Label response arrays remain complete.
No schema migration or speculative indexes are needed for this stage.

Example: `/projects/1/tasks?status=PENDING&type=BUG&priority=HIGH&labelId=2&dueFrom=2026-01-01&dueTo=2026-12-31&page=0&size=10`.

Global task routes remain unavailable. Text search, configurable sorting, and
multiple-label selection are not part of this stage. Issue #7 remains open.
