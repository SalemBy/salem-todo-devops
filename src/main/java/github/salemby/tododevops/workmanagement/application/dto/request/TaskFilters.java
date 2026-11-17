package github.salemby.tododevops.workmanagement.application.dto.request;

import github.salemby.tododevops.workmanagement.domain.TaskStatus;
import github.salemby.tododevops.workmanagement.domain.TaskType;
import github.salemby.tododevops.workmanagement.domain.TaskPriority;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record TaskFilters(TaskStatus status, TaskType type, TaskPriority priority,
        Long labelId, LocalDate dueFrom, LocalDate dueTo) {
    public static TaskFilters parse(String status, String type, String priority,
            String labelId, String dueFrom, String dueTo) {
        Long label = null;
        if (labelId != null) {
            try {
                if (!labelId.matches("[0-9]+")) throw new IllegalArgumentException();
                label = Long.valueOf(labelId);
                if (label <= 0) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) { throw invalid("labelId must be a positive integer"); }
        }
        var from = date("dueFrom", dueFrom);
        var to = date("dueTo", dueTo);
        if (from != null && to != null && from.isAfter(to)) throw invalid("dueFrom must not be after dueTo");
        return new TaskFilters(enumeration(TaskStatus.class, status), enumeration(TaskType.class, type),
            enumeration(TaskPriority.class, priority), label, from, to);
    }

    private static <T extends Enum<T>> T enumeration(Class<T> type, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ex) { throw invalid("Invalid " + type.getSimpleName() + " filter"); }
    }

    private static LocalDate date(String field, String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1) throw new IllegalArgumentException();
            return date;
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            throw invalid(field + " must be a valid yyyy-MM-dd date in years 0001-9999");
        }
    }

    private static ResponseStatusException invalid(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
