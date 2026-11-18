package github.salemby.tododevops.identity.application;

import java.io.Serializable;
import java.time.Instant;

/** Session identity deliberately contains neither a password nor its hash. */
public record AccountPrincipal(Long id, long credentialVersion, Instant authenticatedAt) implements Serializable {}
