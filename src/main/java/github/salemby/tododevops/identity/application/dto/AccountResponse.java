package github.salemby.tododevops.identity.application.dto;

import github.salemby.tododevops.identity.domain.Account;

import java.time.Instant;

public record AccountResponse(Long id, String email, String displayName, boolean active,
        Instant createdAt, Instant updatedAt, boolean emailVerified) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getDisplayName(),
            account.isActive(), account.getCreatedAt(), account.getUpdatedAt(), account.isVerified());
    }
}
