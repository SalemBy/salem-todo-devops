package github.salemby.tododevops.shared.security;

import java.util.Objects;

/**
 * The authenticated account making the current request.
 * Identity resolves it from the session; feature modules receive it as a controller
 * parameter without depending on identity's security classes.
 */
public record CurrentAccount(Long id) {
    public CurrentAccount {
        Objects.requireNonNull(id, "Account id is required");
    }
}
