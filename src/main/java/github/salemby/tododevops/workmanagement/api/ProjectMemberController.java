package github.salemby.tododevops.workmanagement.api;

import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.ProjectMemberService;
import github.salemby.tododevops.workmanagement.application.dto.request.AddMemberRequest;
import github.salemby.tododevops.workmanagement.application.dto.request.ChangeMemberRoleRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.MemberResponse;
import github.salemby.tododevops.workmanagement.application.dto.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/projects/{projectId}/members")
public class ProjectMemberController {
    private final ProjectMemberService service;

    public ProjectMemberController(ProjectMemberService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<MemberResponse> list(CurrentAccount account, @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(account, projectId, page, size);
    }

    @GetMapping("/{accountId}")
    public MemberResponse read(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long accountId) {
        return service.read(account, projectId, accountId);
    }

    @PostMapping
    public ResponseEntity<MemberResponse> add(CurrentAccount account, @PathVariable Long projectId,
            @Valid @RequestBody AddMemberRequest request) {
        var member = service.add(account, projectId, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{accountId}").buildAndExpand(member.accountId()).toUri();
        return ResponseEntity.created(location).body(member);
    }

    @PutMapping("/{accountId}")
    public MemberResponse changeRole(CurrentAccount account, @PathVariable Long projectId,
            @PathVariable Long accountId, @Valid @RequestBody ChangeMemberRoleRequest request) {
        return service.changeRole(account, projectId, accountId, request);
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> remove(CurrentAccount account, @PathVariable Long projectId,
            @PathVariable Long accountId) {
        service.remove(account, projectId, accountId);
        return ResponseEntity.noContent().build();
    }
}
