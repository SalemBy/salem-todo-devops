package github.salemby.tododevops.workmanagement.infrastructure.persistence;

import github.salemby.tododevops.workmanagement.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByProjectId(Long projectId);
    List<Task> findAllByProjectIdAndActive(Long projectId, boolean active);


    Optional<Task> findByIdAndProjectId(Long id, Long projectId);
    Optional<Task> findByIdAndProjectIdAndActiveTrue(Long id, Long projectId);
}
