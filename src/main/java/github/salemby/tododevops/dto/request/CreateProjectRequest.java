package github.salemby.tododevops.dto.request;

import jakarta.validation.constraints.NotBlank;
import github.salemby.tododevops.validation.NotBlankText;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
    @NotBlankText(message = "Name is required")
    @Size(max = 200, message = "Name must contain at most 200 characters")
    String name,
    @NotBlank(message = "Key is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$",
        message = "Key must contain 2 to 10 uppercase letters or digits and start with a letter")
    @Pattern(regexp = "^(?!LEGACY$).*$", message = "LEGACY is reserved for imported tasks")
    String key,
    @Size(max = 2000, message = "Description must contain at most 2000 characters")
    String description
) {
}
