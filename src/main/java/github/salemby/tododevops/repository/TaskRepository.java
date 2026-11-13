package github.salemby.tododevops.repository;

import github.salemby.tododevops.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByActive(boolean active);

    Optional<Task> findByIdAndActiveTrue(Long id);
}
