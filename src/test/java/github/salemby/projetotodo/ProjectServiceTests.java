package github.salemby.projetotodo;

import github.salemby.projetotodo.dto.request.CreateProjectRequest;
import github.salemby.projetotodo.repository.ProjectRepository;
import github.salemby.projetotodo.service.ProjectService;
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
}
