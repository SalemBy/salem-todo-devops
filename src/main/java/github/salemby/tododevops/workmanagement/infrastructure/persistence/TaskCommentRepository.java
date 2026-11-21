package github.salemby.tododevops.workmanagement.infrastructure.persistence;

import github.salemby.tododevops.workmanagement.domain.TaskComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    Page<TaskComment> findByTaskIdAndActiveTrue(Long taskId, Pageable pageable);

    Optional<TaskComment> findByIdAndTaskIdAndActiveTrue(Long id, Long taskId);
}
