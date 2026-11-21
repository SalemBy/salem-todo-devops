package github.salemby.tododevops.administration.application.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.JsonNode;

/** The account to make an OWNER of an ownerless project. */
public record OwnerRequest(
    @NotNull(message = "Account ID is required")
    @Positive(message = "Account ID must be positive")
    Long accountId
) {
    /** Only a JSON integer is an ID: Jackson would otherwise accept "42" and truncate 42.5. */
    @JsonCreator
    static OwnerRequest of(@JsonProperty("accountId") JsonNode accountId) {
        if (accountId == null || accountId.isNull()) return new OwnerRequest(null);
        if (!accountId.isIntegralNumber() || !accountId.canConvertToLong())
            throw new IllegalArgumentException("Account ID must be an integer");
        return new OwnerRequest(accountId.longValue());
    }
}
