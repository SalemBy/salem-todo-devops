package github.salemby.tododevops.workmanagement.application.dto.response;

import java.util.List;

/** What releasing a deactivated account changed: assignments cleared, and projects left with no active owner. */
public record AccountRelease(int unassignedTasks, List<Long> ownerlessProjectIds) {}
