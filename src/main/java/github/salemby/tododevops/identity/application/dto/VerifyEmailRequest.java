package github.salemby.tododevops.identity.application.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** The token from a verification email (#71). */
public record VerifyEmailRequest(
    @NotBlank(message = "Token is required")
    @Size(max = 100, message = "Token must contain at most 100 characters")
    String token
) {
    /** Only a JSON string is a token: Jackson would otherwise accept the number 5 as the text 5. */
    @JsonCreator
    static VerifyEmailRequest of(@JsonProperty("token") JsonNode token) {
        if (token == null || token.isNull()) return new VerifyEmailRequest(null);
        if (!token.isString()) throw new IllegalArgumentException("Token must be a string");
        return new VerifyEmailRequest(token.stringValue());
    }
}
