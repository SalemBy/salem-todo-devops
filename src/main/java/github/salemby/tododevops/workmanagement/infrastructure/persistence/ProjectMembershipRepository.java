package github.salemby.tododevops.workmanagement.infrastructure.persistence;

import github.salemby.tododevops.workmanagement.domain.ProjectMembership;
import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, Long> {
    Optional<ProjectMembership> findByProjectIdAndAccountId(Long projectId, Long accountId);

    /** The account's memberships with their projects, in one query, ordered by project ID. */
    @Query("select m from ProjectMembership m join fetch m.project p where m.accountId = :accountId order by p.id")
    List<ProjectMembership> findAllWithProjectByAccountId(@Param("accountId") Long accountId);

    @Query("""
        select m from ProjectMembership m join fetch m.project p
        where m.accountId = :accountId and p.active = :active order by p.id""")
    List<ProjectMembership> findAllWithProjectByAccountIdAndActive(@Param("accountId") Long accountId,
                                                                   @Param("active") boolean active);

    /** A page of a project's members by rank (owners, managers, members, viewers), each by account ID. */
    @Query(value = """
        select m from ProjectMembership m where m.project.id = :projectId
        order by case m.role
            when github.salemby.tododevops.workmanagement.domain.ProjectRole.OWNER then 0
            when github.salemby.tododevops.workmanagement.domain.ProjectRole.MANAGER then 1
            when github.salemby.tododevops.workmanagement.domain.ProjectRole.MEMBER then 2
            else 3 end, m.accountId""",
        countQuery = "select count(m) from ProjectMembership m where m.project.id = :projectId")
    Page<ProjectMembership> findPageByProjectId(@Param("projectId") Long projectId, Pageable pageable);

    List<ProjectMembership> findAllByProjectIdAndRole(Long projectId, ProjectRole role);

    List<ProjectMembership> findAllByAccountIdAndRole(Long accountId, ProjectRole role);
}
