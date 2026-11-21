package github.salemby.tododevops.identity.application.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import github.salemby.tododevops.shared.validation.NoNulText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * The token from a verification email and the password chosen at registration (#71): together they prove both the
 * inbox and the registration, so someone who re-registers a pending email cannot have the owner verify it for them.
 */
public record VerifyEmailRequest(
    @NotBlank(message = "Token is required")
    @Size(max = 100, message = "Token must contain at most 100 characters")
    String token,
    @NotNull @NoNulText @Size(min = 1, max = 256) String password
) {
    /** Only a JSON string is a token: Jackson would otherwise accept the number 5 as the text 5. */
    @JsonCreator
    static VerifyEmailRequest of(@JsonProperty("token") JsonNode token, @JsonProperty("password") String password) {
        if (token == null || token.isNull()) return new VerifyEmailRequest(null, password);
        if (!token.isString()) throw new IllegalArgumentException("Token must be a string");
        return new VerifyEmailRequest(token.stringValue(), password);
    }

    @Override public String toString() { return "VerifyEmailRequest[REDACTED]"; }
}
