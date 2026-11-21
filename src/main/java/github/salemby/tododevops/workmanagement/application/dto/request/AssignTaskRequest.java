package github.salemby.tododevops.workmanagement.application.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.JsonNode;

/** The account to make responsible for a task. */
public record AssignTaskRequest(
    @NotNull(message = "Account ID is required")
    @Positive(message = "Account ID must be positive")
    Long accountId
) {
    /** Only a JSON integer is an ID: Jackson would otherwise accept "42" and truncate 42.5. */
    @JsonCreator
    static AssignTaskRequest of(@JsonProperty("accountId") JsonNode accountId) {
        if (accountId == null || accountId.isNull()) return new AssignTaskRequest(null);
        if (!accountId.isIntegralNumber() || !accountId.canConvertToLong())
            throw new IllegalArgumentException("Account ID must be an integer");
        return new AssignTaskRequest(accountId.longValue());
    }
}
