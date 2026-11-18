package github.salemby.tododevops;

import github.salemby.tododevops.workmanagement.application.dto.request.CreateProjectRequest;
import github.salemby.tododevops.shared.security.CurrentAccount;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectMembershipRepository;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.ProjectRepository;
import github.salemby.tododevops.workmanagement.application.ProjectService;
import github.salemby.tododevops.workmanagement.application.ProjectAccess;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectServiceTests {
    @Test
    void doesNotMisreportUnrelatedIntegrityFailuresAsDuplicateKeys() {
        var repository = mock(ProjectRepository.class);
        var failure = new DataIntegrityViolationException("Unrelated constraint failure",
            new SQLException("Not-null violation", "23502"));
        when(repository.saveAndFlush(any())).thenThrow(failure);
        var service = new ProjectService(repository, mock(ProjectMembershipRepository.class), mock(ProjectAccess.class));
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
            () -> service.create(new CurrentAccount(1L), new CreateProjectRequest("Valid", "OK", null))));
    }

    @Test
    void translatesLocalizedPostgresDuplicateKeyUsingStructuredFields() {
        var repository = mock(ProjectRepository.class);
        var metadata = mock(org.postgresql.util.ServerErrorMessage.class);
        when(metadata.getSQLState()).thenReturn("23505");
        when(metadata.getConstraint()).thenReturn("uk_projects_key");
        when(metadata.toString()).thenReturn("ERRO: valor da chave duplicado");
        var postgres = new org.postgresql.util.PSQLException(metadata);
        when(repository.saveAndFlush(any())).thenThrow(
            new DataIntegrityViolationException("Localized database failure", postgres));
        var service = new ProjectService(repository, mock(ProjectMembershipRepository.class), mock(ProjectAccess.class));
        var result = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.create(new CurrentAccount(1L), new CreateProjectRequest("Valid", "OK", null)));
        assertEquals(409, result.getStatusCode().value());
        assertEquals("Project key already exists", result.getReason());
    }
}
