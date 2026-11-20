package github.salemby.tododevops.workmanagement.infrastructure.persistence;

import github.salemby.tododevops.workmanagement.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Project p where p.id = :id")
    Optional<Project> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
