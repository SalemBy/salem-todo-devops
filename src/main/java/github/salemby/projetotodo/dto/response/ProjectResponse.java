package github.salemby.projetotodo.dto.response;

import java.time.Instant;

public record ProjectResponse(Long id, String name, String key, String description,
                              boolean active, Instant createdAt, Instant updatedAt) {
}
