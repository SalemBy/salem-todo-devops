package github.salemby.tododevops.workmanagement.infrastructure.persistence;

import github.salemby.tododevops.workmanagement.domain.Task;
import github.salemby.tododevops.workmanagement.application.dto.request.TaskFilters;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;

public final class TaskSpecifications {
    private TaskSpecifications() {}

    public static Specification<Task> matching(Long projectId, Boolean active, TaskFilters filters) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("project").get("id"), projectId));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (filters.status() != null) predicates.add(cb.equal(root.get("status"), filters.status()));
            if (filters.type() != null) predicates.add(cb.equal(root.get("type"), filters.type()));
            if (filters.priority() != null) predicates.add(cb.equal(root.get("priority"), filters.priority()));
            if (filters.dueFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), filters.dueFrom()));
            if (filters.dueTo() != null) predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), filters.dueTo()));
            // A single label ID yields at most one association per task, so counts remain exact.
            if (filters.labelId() != null) predicates.add(cb.equal(root.join("labels").get("id"), filters.labelId()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
