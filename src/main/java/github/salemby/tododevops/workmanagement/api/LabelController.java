package github.salemby.tododevops.workmanagement.api;

import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.LabelService;
import github.salemby.tododevops.workmanagement.application.dto.request.LabelRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.LabelResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/labels")
public class LabelController {
    private final LabelService service;
    public LabelController(LabelService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<LabelResponse> create(CurrentAccount account, @PathVariable Long projectId, @Valid @RequestBody LabelRequest request) {
        var label = service.create(account, projectId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(label.id()).toUri()).body(label);
    }

    @GetMapping
    public List<LabelResponse> list(CurrentAccount account, @PathVariable Long projectId, @RequestParam(defaultValue = "true") String active) {
        return service.list(account, projectId, active);
    }

    @GetMapping("/{labelId}")
    public LabelResponse read(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long labelId) {
        return service.read(account, projectId, labelId);
    }

    @PutMapping("/{labelId}")
    public LabelResponse update(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long labelId, @Valid @RequestBody LabelRequest request) {
        return service.update(account, projectId, labelId, request);
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<Void> archive(CurrentAccount account, @PathVariable Long projectId, @PathVariable Long labelId) {
        service.archive(account, projectId, labelId);
        return ResponseEntity.noContent().build();
    }
}
