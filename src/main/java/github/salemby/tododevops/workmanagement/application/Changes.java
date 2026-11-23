package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.workmanagement.domain.FieldChange;
import github.salemby.tododevops.workmanagement.domain.Label;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** The changed fields of one activity event, in the order they were added (#12). */
final class Changes {
    private final List<FieldChange> list = new ArrayList<>();

    /** Records the field with both values as text, when they differ. */
    Changes value(String field, Object before, Object after) {
        if (!Objects.equals(before, after)) list.add(new FieldChange(field, format(before), format(after)));
        return this;
    }

    /** Records only that a long-text field changed: its values are never stored. */
    Changes text(String field, Object before, Object after) {
        if (!Objects.equals(before, after)) list.add(new FieldChange(field, null, null));
        return this;
    }

    boolean isEmpty() {
        return list.isEmpty();
    }

    List<FieldChange> list() {
        return List.copyOf(list);
    }

    /** The label IDs ascending, comma-separated, or null for no labels. */
    static String labelIds(Collection<Label> labels) {
        if (labels.isEmpty()) return null;
        return labels.stream().map(Label::getId).sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String format(Object value) {
        if (value == null) return null;
        if (value instanceof Enum<?> e) return e.name();
        return value.toString();
    }
}
