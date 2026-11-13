package github.salemby.projetotodo.repository;

import github.salemby.projetotodo.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByActive(boolean active);

    Optional<Project> findByIdAndActiveTrue(Long id);
}
