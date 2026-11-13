package github.salemby.tododevops;

import github.salemby.tododevops.dto.request.CreateProjectRequest;
import github.salemby.tododevops.repository.ProjectRepository;
import github.salemby.tododevops.service.ProjectService;
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
        var service = new ProjectService(repository);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
            () -> service.create(new CreateProjectRequest("Valid", "OK", null))));
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
        var service = new ProjectService(repository);
        var result = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.create(new CreateProjectRequest("Valid", "OK", null)));
        assertEquals(409, result.getStatusCode().value());
        assertEquals("Project key already exists", result.getReason());
    }
}
