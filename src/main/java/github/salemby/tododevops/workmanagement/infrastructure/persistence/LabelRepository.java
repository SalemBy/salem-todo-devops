package github.salemby.tododevops.workmanagement.infrastructure.persistence;

import github.salemby.tododevops.workmanagement.domain.Label;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface LabelRepository extends JpaRepository<Label, Long> {
    Optional<Label> findByIdAndProjectId(Long id, Long projectId);
    List<Label> findAllByProjectIdOrderById(Long projectId);
    List<Label> findAllByProjectIdAndActiveOrderById(Long projectId, boolean active);
    List<Label> findAllByProjectIdAndIdIn(Long projectId, java.util.Collection<Long> ids);

    @Query(value = "select exists(select 1 from labels where project_id = :projectId and lower(name) = lower(:name) and id <> coalesce(:excluded, 0))", nativeQuery = true)
    boolean nameExists(@Param("projectId") Long projectId, @Param("name") String name,
                       @Param("excluded") Long excluded);
}
