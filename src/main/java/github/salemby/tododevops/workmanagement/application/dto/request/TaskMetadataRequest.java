package github.salemby.tododevops.workmanagement.application.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import github.salemby.tododevops.workmanagement.domain.TaskType;
import github.salemby.tododevops.workmanagement.domain.TaskPriority;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;

/** Setters are invoked only for supplied properties, preserving omission on PUT. */
public abstract class TaskMetadataRequest {
    private TaskType type;
    private TaskPriority priority;
    private LocalDate dueDate;
    private boolean dueDatePresent;
    private java.util.List<Long> labelIds;

    @JsonSetter("labelIds")
    public void setLabelIds(JsonNode value) {
        if (value == null || !value.isArray()) throw new IllegalArgumentException("Label IDs must be an array");
        var ids = new java.util.LinkedHashSet<Long>();
        for (JsonNode id : value) {
            if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0 || !ids.add(id.longValue()))
                throw new IllegalArgumentException("Label IDs must be distinct positive integers");
        }
        labelIds = java.util.List.copyOf(ids);
    }

    public java.util.List<Long> labelIds() { return labelIds; }

    @JsonSetter("type")
    public void setType(JsonNode value) { type = TaskType.valueOf(requiredText(value)); }
    @JsonSetter("priority")
    public void setPriority(JsonNode value) { priority = TaskPriority.valueOf(requiredText(value)); }
    @JsonSetter("dueDate")
    public void setDueDate(JsonNode value) {
        dueDatePresent = true;
        if (value == null || value.isNull()) { dueDate = null; return; }
        String date = requiredText(value);
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || date.startsWith("0000"))
            throw new IllegalArgumentException("Due date must use yyyy-MM-dd in years 0001-9999");
        dueDate = LocalDate.parse(date);
    }

    private static String requiredText(JsonNode value) {
        if (value == null || !value.isString()) throw new IllegalArgumentException("Expected a non-null string");
        return value.stringValue();
    }
    public TaskType type() { return type; }
    public TaskPriority priority() { return priority; }
    public LocalDate dueDate() { return dueDate; }
    public boolean dueDatePresent() { return dueDatePresent; }
}
