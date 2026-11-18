package github.salemby.tododevops.identity.application.dto;

/** Public account details other modules may show, such as in project member lists. */
public record AccountSummary(Long id, String email, String displayName, boolean active) {}
