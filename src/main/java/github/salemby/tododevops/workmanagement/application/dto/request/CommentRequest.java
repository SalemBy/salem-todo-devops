package github.salemby.tododevops.workmanagement.application.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import github.salemby.tododevops.shared.validation.NoNulText;
import github.salemby.tododevops.shared.validation.NotBlankText;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** A comment's text, stored exactly as sent and never rendered. */
public record CommentRequest(
    @NotBlankText(message = "Comment body is required")
    @NoNulText
    @Size(max = 10000, message = "Comment body must contain at most 10000 characters")
    String body
) {
    /** Only a JSON string is text: Jackson would otherwise accept the number 5 as the text 5. */
    @JsonCreator
    static CommentRequest of(@JsonProperty("body") JsonNode body) {
        if (body == null || body.isNull()) return new CommentRequest(null);
        if (!body.isString()) throw new IllegalArgumentException("Comment body must be a string");
        return new CommentRequest(body.stringValue());
    }
}
