package github.salemby.tododevops.workmanagement.infrastructure.persistence;

import github.salemby.tododevops.workmanagement.domain.ActivityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Events are only inserted and read; nothing here updates or deletes them. */
public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {
    Page<ActivityEvent> findByProjectId(Long projectId, Pageable pageable);

    Page<ActivityEvent> findByProjectIdAndTaskId(Long projectId, Long taskId, Pageable pageable);

    /** Loads the changes of a page of events in one query, after paging. */
    @EntityGraph(attributePaths = "changes")
    List<ActivityEvent> findAllByIdIn(Collection<Long> ids);
}
