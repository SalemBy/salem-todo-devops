package github.salemby.tododevops.identity.application.dto;

import java.time.Instant;

public record AccountResponse(Long id, String email, String displayName, boolean active,
        Instant createdAt, Instant updatedAt) {}
