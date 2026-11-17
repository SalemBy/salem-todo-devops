package github.salemby.tododevops.workmanagement.infrastructure.persistence;

import github.salemby.tododevops.workmanagement.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "labels")
    List<Task> findAllByProjectId(Long projectId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "labels")
    List<Task> findAllByProjectIdAndActive(Long projectId, boolean active);


    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "labels")
    Optional<Task> findByIdAndProjectId(Long id, Long projectId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "labels")
    Optional<Task> findByIdAndProjectIdAndActiveTrue(Long id, Long projectId);
}
