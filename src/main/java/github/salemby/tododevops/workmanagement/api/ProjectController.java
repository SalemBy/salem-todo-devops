package github.salemby.tododevops.workmanagement.api;

import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.application.dto.request.CreateProjectRequest;
import github.salemby.tododevops.workmanagement.application.dto.request.UpdateProjectRequest;
import github.salemby.tododevops.workmanagement.application.dto.response.ProjectResponse;
import github.salemby.tododevops.workmanagement.application.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(CurrentAccount account,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.create(account, request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(project.id()).toUri();
        return ResponseEntity.created(location).body(project);
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> findAll(CurrentAccount account,
            @RequestParam(defaultValue = "true") String active) {
        return ResponseEntity.ok(projectService.findAll(account, active));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> findById(CurrentAccount account, @PathVariable Long id) {
        return ResponseEntity.ok(projectService.findById(account, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> update(CurrentAccount account, @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.update(account, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(CurrentAccount account, @PathVariable Long id) {
        projectService.delete(account, id);
        return ResponseEntity.noContent().build();
    }
}
