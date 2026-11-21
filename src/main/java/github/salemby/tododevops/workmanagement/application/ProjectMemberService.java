package github.salemby.tododevops.workmanagement.application;

import github.salemby.tododevops.identity.application.AccountDirectory;
import github.salemby.tododevops.identity.application.dto.AccountSummary;
import github.salemby.tododevops.shared.persistence.Timestamps;
import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.dto.request.AddMemberRequest;
import github.salemby.tododevops.workmanagement.application.dto.request.ChangeMemberRoleRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.MemberResponse;
import github.salemby.tododevops.workmanagement.application.dto.response.PageResponse;
import github.salemby.tododevops.workmanagement.domain.ProjectMembership;
import github.salemby.tododevops.workmanagement.domain.ProjectPermission;
import github.salemby.tododevops.workmanagement.domain.ProjectRole;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectMembershipRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.TaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Project membership management (issue #9). Every write locks the project row through
 * {@link ProjectAccess}, so membership changes serialize with each other and with business
 * writes, and the last-owner rule holds under concurrent requests.
 */
@Service
@Validated
public class ProjectMemberService {
    static final String MEMBER_NOT_FOUND = "Member not found";
    static final String LAST_OWNER = "A project must keep at least one active owner";

    private final ProjectAccess access;
    private final ProjectMembershipRepository memberships;
    private final AccountDirectory directory;
    private final TaskRepository tasks;

    public ProjectMemberService(ProjectAccess access, ProjectMembershipRepository memberships, AccountDirectory directory,
                                TaskRepository tasks) {
        this.tasks = tasks;
        this.access = access;
        this.memberships = memberships;
        this.directory = directory;
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberResponse> list(@NotNull CurrentAccount account, Long projectId, int page, int size) {
        Paging.require(page, size);
        access.read(projectId, account);
        var found = memberships.findPageByProjectId(projectId, PageRequest.of(page, size));
        var accounts = directory.findByIds(found.getContent().stream().map(ProjectMembership::getAccountId).toList());
        return new PageResponse<>(found.getContent().stream().map(m -> toResponse(m, accounts)).toList(),
            page, size, found.getTotalElements(), found.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MemberResponse read(@NotNull CurrentAccount account, Long projectId, Long accountId) {
        requireValidAccountId(accountId);
        access.read(projectId, account);
        var membership = requireMembership(projectId, accountId);
        return toResponse(membership, directory.findByIds(List.of(accountId)));
    }

    @Transactional
    public MemberResponse add(@NotNull CurrentAccount account, Long projectId, @NotNull @Valid AddMemberRequest request) {
        var granted = access.lock(projectId, account, ProjectPermission.MEMBERS_MANAGE);
        // 422, not 404: in this API 404 means the project was not found.
        AccountSummary target = directory.findActiveByEmail(request.email()).orElseThrow(() ->
            new ResponseStatusException(HttpStatusCode.valueOf(422), "Account not found"));
        if (memberships.findByProjectIdAndAccountId(projectId, target.id()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is already a member");
        var saved = memberships.saveAndFlush(new ProjectMembership(granted.project(), target.id(), request.role()));
        return toResponse(saved, Map.of(target.id(), target));
    }

    @Transactional
    public MemberResponse changeRole(@NotNull CurrentAccount account, Long projectId, Long accountId,
                                     @NotNull @Valid ChangeMemberRoleRequest request) {
        requireValidAccountId(accountId);
        access.lock(projectId, account, ProjectPermission.MEMBERS_MANAGE);
        var membership = requireMembership(projectId, accountId);
        if (membership.getRole() != request.role()) {
            if (membership.getRole() == ProjectRole.OWNER) requireAnotherActiveOwner(projectId, accountId);
            // Only roles that may write tasks can hold one (#10).
            if (!request.role().allows(ProjectPermission.TASK_WRITE)) tasks.clearAssignee(projectId, accountId, Timestamps.now());
            membership.changeRole(request.role());
            membership = memberships.saveAndFlush(membership);
        }
        return toResponse(membership, directory.findByIds(List.of(accountId)));
    }

    /** OWNERs may remove anyone; any member may remove themselves (leave). */
    @Transactional
    public void remove(@NotNull CurrentAccount account, Long projectId, Long accountId) {
        requireValidAccountId(accountId);
        boolean leaving = accountId.equals(account.id());
        access.lock(projectId, account, leaving ? ProjectPermission.PROJECT_READ : ProjectPermission.MEMBERS_MANAGE);
        var membership = requireMembership(projectId, accountId);
        if (membership.getRole() == ProjectRole.OWNER) requireAnotherActiveOwner(projectId, accountId);
        tasks.clearAssignee(projectId, accountId, Timestamps.now()); // the assignee FK refuses the delete otherwise (#10)
        memberships.delete(membership);
        memberships.flush();
    }

    /** Callers hold the project lock, so no concurrent change can remove the remaining owner. */
    private void requireAnotherActiveOwner(Long projectId, Long changingAccountId) {
        var otherOwners = memberships.findAllByProjectIdAndRole(projectId, ProjectRole.OWNER).stream()
            .map(ProjectMembership::getAccountId).filter(id -> !id.equals(changingAccountId)).toList();
        boolean anotherActive = directory.findByIds(otherOwners).values().stream().anyMatch(AccountSummary::active);
        if (!anotherActive) throw new ResponseStatusException(HttpStatus.CONFLICT, LAST_OWNER);
    }

    private ProjectMembership requireMembership(Long projectId, Long accountId) {
        return memberships.findByProjectIdAndAccountId(projectId, accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, MEMBER_NOT_FOUND));
    }

    private static void requireValidAccountId(Long accountId) {
        if (accountId == null || accountId <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account ID must be positive");
    }

    private static MemberResponse toResponse(ProjectMembership membership, Map<Long, AccountSummary> accounts) {
        var summary = accounts.get(membership.getAccountId());
        if (summary == null) throw new IllegalStateException("Membership references a missing account");
        return new MemberResponse(membership.getAccountId(), summary.displayName(), summary.email(),
            membership.getRole(), membership.getCreatedAt(), membership.getUpdatedAt());
    }
}
