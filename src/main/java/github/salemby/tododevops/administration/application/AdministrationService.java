package github.salemby.tododevops.administration.application;

import github.salemby.tododevops.administration.application.dto.DeactivationResponse;
import github.salemby.tododevops.administration.application.dto.OwnerRequest;
import github.salemby.tododevops.identity.application.AccountAdministration;
import github.salemby.tododevops.identity.application.dto.AccountResponse;
import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.ProjectMemberService;
import github.salemby.tododevops.workmanagement.application.dto.response.MemberResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * System administration across identity and work management (#67). The security filter has already
 * confirmed the caller is an administrator. Project changes are recorded in activity history (#12) with the
 * administrator as actor; account changes are logged at INFO.
 */
@Service
public class AdministrationService {
    private static final Logger log = LoggerFactory.getLogger(AdministrationService.class);
    private final AccountAdministration accounts;
    private final ProjectMemberService members;

    public AdministrationService(AccountAdministration accounts, ProjectMemberService members) {
        this.accounts = accounts;
        this.members = members;
    }

    /** One transaction: the account is disabled and its assignments cleared together, or neither happens. */
    @Transactional
    public DeactivationResponse deactivate(CurrentAccount admin, Long accountId) {
        var account = accounts.deactivate(accountId);
        var release = members.release(accountId, admin.id());
        log.info("Administrator {} deactivated account {}", admin.id(), accountId);
        return new DeactivationResponse(account, release.unassignedTasks(), release.ownerlessProjectIds());
    }

    public MemberResponse recoverOwner(CurrentAccount admin, Long projectId, OwnerRequest request) {
        var owner = members.recoverOwner(projectId, request.accountId(), admin.id());
        log.info("Administrator {} made account {} an owner of project {}", admin.id(), request.accountId(), projectId);
        return owner;
    }

    public AccountResponse reactivate(CurrentAccount admin, Long accountId) {
        var account = accounts.reactivate(accountId);
        log.info("Administrator {} reactivated account {}", admin.id(), accountId);
        return account;
    }
}
