package github.salemby.tododevops.workmanagement.application.dto.response;

import github.salemby.tododevops.workmanagement.domain.FieldChange;

public record ChangeResponse(String field, String before, String after) {
    public static ChangeResponse from(FieldChange change) {
        return new ChangeResponse(change.field(), change.before(), change.after());
    }
}
