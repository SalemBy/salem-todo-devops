package github.salemby.tododevops.workmanagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One changed field of an activity event; values are text, or null for none or for unrecorded long text. */
@Embeddable
public record FieldChange(
    @Column(name = "field", nullable = false, length = 40) String field,
    @Column(name = "before_value") String before,
    @Column(name = "after_value") String after) {}
