package github.salemby.tododevops;

import github.salemby.tododevops.workmanagement.application.dto.request.CreateProjectRequest;
import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectMembershipRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import github.salemby.tododevops.workmanagement.application.ProjectService;
import github.salemby.tododevops.workmanagement.application.ProjectAccess;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static github.salemby.tododevops.support.PostgresFailures.integrityViolation;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectServiceTests {
    private final ProjectRepository repository = mock(ProjectRepository.class);
    private final ProjectService service =
        new ProjectService(repository, mock(ProjectMembershipRepository.class), mock(ProjectAccess.class));
    private final CreateProjectRequest request = new CreateProjectRequest("Valid", "OK", null);

    @Test
    void doesNotMisreportUnrelatedIntegrityFailuresAsDuplicateKeys() {
        var failure = integrityViolation("23502", null);
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
            () -> service.create(new CurrentAccount(1L), request)));
    }

    @Test
    void translatesLocalizedPostgresDuplicateKeyUsingStructuredFields() {
        when(repository.saveAndFlush(any())).thenThrow(integrityViolation("23505", "uk_projects_key"));
        var result = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.create(new CurrentAccount(1L), request));
        assertEquals(409, result.getStatusCode().value());
        assertEquals("Project key already exists", result.getReason());
    }

    @Test
    void doesNotReportOtherUniqueConstraintsAsDuplicateKeys() {
        var failure = integrityViolation("23505", "uk_project_memberships_project_account");
        when(repository.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
            () -> service.create(new CurrentAccount(1L), request)));
    }
}
