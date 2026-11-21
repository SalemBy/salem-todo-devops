package github.salemby.tododevops.administration.api;

import github.salemby.tododevops.administration.application.AdministrationService;
import github.salemby.tododevops.administration.application.dto.DeactivationResponse;
import github.salemby.tododevops.administration.application.dto.OwnerRequest;
import github.salemby.tododevops.identity.application.dto.AccountResponse;
import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.dto.response.MemberResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** System administration routes; non-administrators never reach them (identity's AdministratorFilter). */
@RestController
@RequestMapping("/admin")
public class AdministrationController {
    private final AdministrationService service;

    public AdministrationController(AdministrationService service) {
        this.service = service;
    }

    @PostMapping("/accounts/{accountId}/deactivate")
    public DeactivationResponse deactivate(CurrentAccount admin, @PathVariable Long accountId) {
        return service.deactivate(admin, accountId);
    }

    @PostMapping("/projects/{projectId}/owners")
    public MemberResponse recoverOwner(CurrentAccount admin, @PathVariable Long projectId, @Valid @RequestBody OwnerRequest request) {
        return service.recoverOwner(admin, projectId, request);
    }

    @PostMapping("/accounts/{accountId}/reactivate")
    public AccountResponse reactivate(CurrentAccount admin, @PathVariable Long accountId) {
        return service.reactivate(admin, accountId);
    }
}
