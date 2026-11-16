package github.salemby.tododevops;

import github.salemby.tododevops.shared.config.ValidationConfiguration;
import github.salemby.tododevops.workmanagement.application.*;
import github.salemby.tododevops.workmanagement.application.dto.request.*;
import github.salemby.tododevops.workmanagement.domain.TaskStatus;
import github.salemby.tododevops.workmanagement.infrastructure.persistence.*;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Real Spring validation proxies, without HTTP, JPA or a database. */
@SpringJUnitConfig(ServiceValidationTests.Config.class)
class ServiceValidationTests {
    @Configuration(proxyBeanMethods = false)
    @Import({ValidationConfiguration.class, TaskService.class, ProjectService.class})
    static class Config {
        @Bean static MethodValidationPostProcessor methodValidation() {
            return new MethodValidationPostProcessor();
        }
        @Bean TaskRepository tasks() { return mock(TaskRepository.class); }
        @Bean ProjectRepository projects() { return mock(ProjectRepository.class); }
    }

    @Autowired TaskService taskService;
    @Autowired ProjectService projectService;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;

    @AfterEach
    void rejectsBeforeAnyRepositoryAccess() {
        verifyNoInteractions(tasks, projects);
    }

    @Test
    void validatesCreateRequestsAtServiceBoundary() {
        assertThrows(ConstraintViolationException.class,
            () -> taskService.create(1L, new CreateTaskRequest("\u200b", null)));
        assertThrows(ConstraintViolationException.class,
            () -> projectService.create(new CreateProjectRequest("\u200b", "VALID", null)));
        assertThrows(ConstraintViolationException.class,
            () -> projectService.create(new CreateProjectRequest("Valid", " ", null)));
    }

    @Test
    void validatesUpdateRequestsAtServiceBoundary() {
        assertThrows(ConstraintViolationException.class,
            () -> taskService.update(1L, 2L, new UpdateTaskRequest("\u200b", null, TaskStatus.PENDING)));
        assertThrows(ConstraintViolationException.class,
            () -> projectService.update(1L, new UpdateProjectRequest("\u200b", null)));
    }

    @Test
    void rejectsNullRequests() {
        assertThrows(ConstraintViolationException.class, () -> taskService.create(1L, null));
        assertThrows(ConstraintViolationException.class, () -> projectService.create(null));
        assertThrows(ConstraintViolationException.class, () -> taskService.update(1L, 2L, null));
        assertThrows(ConstraintViolationException.class, () -> projectService.update(1L, null));
    }
}
