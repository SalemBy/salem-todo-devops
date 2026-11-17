package github.salemby.tododevops.workmanagement.api;

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
    public ResponseEntity<LabelResponse> create(@PathVariable Long projectId, @Valid @RequestBody LabelRequest request) {
        var label = service.create(projectId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
            .buildAndExpand(label.id()).toUri()).body(label);
    }

    @GetMapping
    public List<LabelResponse> list(@PathVariable Long projectId, @RequestParam(defaultValue = "true") String active) {
        return service.list(projectId, active);
    }

    @GetMapping("/{labelId}")
    public LabelResponse read(@PathVariable Long projectId, @PathVariable Long labelId) {
        return service.read(projectId, labelId);
    }

    @PutMapping("/{labelId}")
    public LabelResponse update(@PathVariable Long projectId, @PathVariable Long labelId, @Valid @RequestBody LabelRequest request) {
        return service.update(projectId, labelId, request);
    }

    @DeleteMapping("/{labelId}")
    public ResponseEntity<Void> archive(@PathVariable Long projectId, @PathVariable Long labelId) {
        service.archive(projectId, labelId);
        return ResponseEntity.noContent().build();
    }
}
