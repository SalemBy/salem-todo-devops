package github.salemby.tododevops;

import github.salemby.tododevops.dto.request.*;
import github.salemby.tododevops.enums.TaskStatus;
import github.salemby.tododevops.repository.ProjectRepository;
import github.salemby.tododevops.repository.TaskRepository;
import github.salemby.tododevops.service.ProjectService;
import github.salemby.tododevops.service.TaskService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(classes = SalemTodoDevOpsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ServiceValidationTests {
    @Autowired TaskService taskService;
    @Autowired ProjectService projectService;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u00a0", "\u2007", "\u202f", "\u200b"})
    void rejectsBlankTaskTitlesBeforePersisting(String title) {
        long count = tasks.count();
        assertThrows(ConstraintViolationException.class,
            () -> taskService.create(new CreateTaskRequest(title, null)));
        assertEquals(count, tasks.count());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u00a0", "\u2007", "\u202f", "\u200b"})
    void rejectsBlankProjectNamesBeforePersisting(String name) {
        long count = projects.count();
        assertThrows(ConstraintViolationException.class,
            () -> projectService.create(new CreateProjectRequest(name, "VALID", null)));
        assertEquals(count, projects.count());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u00a0", "\u2007", "\u202f", "\u200b"})
    void rejectsBlankProjectKeysBeforePersisting(String key) {
        long count = projects.count();
        assertThrows(ConstraintViolationException.class,
            () -> projectService.create(new CreateProjectRequest("Valid", key, null)));
        assertEquals(count, projects.count());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u00a0", "\u2007", "\u202f", "\u200b"})
    void rejectsBlankUpdatesWithoutChangingRows(String text) {
        var task = taskService.create(new CreateTaskRequest("Original task", null));
        var project = projectService.create(new CreateProjectRequest("Original project", "ORIGINAL", null));
        assertThrows(ConstraintViolationException.class,
            () -> taskService.update(task.id(), new UpdateTaskRequest(text, null, TaskStatus.PENDING)));
        assertThrows(ConstraintViolationException.class,
            () -> projectService.update(project.id(), new UpdateProjectRequest(text, null)));
        assertEquals("Original task", tasks.findById(task.id()).orElseThrow().getTitle());
        assertEquals("Original project", projects.findById(project.id()).orElseThrow().getName());
    }

    @Test
    void rejectsNullRequests() {
        assertThrows(ConstraintViolationException.class, () -> taskService.create(null));
        assertThrows(ConstraintViolationException.class, () -> projectService.create(null));
    }
}
