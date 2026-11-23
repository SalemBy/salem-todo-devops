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
    private static final github.salemby.tododevops.shared.security.CurrentAccount ACCOUNT =
        new github.salemby.tododevops.shared.security.CurrentAccount(1L);
    @Configuration(proxyBeanMethods = false)
    @Import({ValidationConfiguration.class, TaskService.class, ProjectService.class, ProjectAccess.class})
    static class Config {
        @Bean static MethodValidationPostProcessor methodValidation() {
            return new MethodValidationPostProcessor();
        }
        @Bean TaskRepository tasks() { return mock(TaskRepository.class); }
        @Bean ProjectRepository projects() { return mock(ProjectRepository.class); }
        @Bean LabelRepository labels() { return mock(LabelRepository.class); }
        @Bean github.salemby.tododevops.workmanagement.application.ActivityLog activity() {
            return mock(github.salemby.tododevops.workmanagement.application.ActivityLog.class);
        }
        @Bean ProjectMembershipRepository memberships() { return mock(ProjectMembershipRepository.class); }
        @Bean github.salemby.tododevops.identity.application.AccountDirectory directory() {
            return mock(github.salemby.tododevops.identity.application.AccountDirectory.class);
        }
    }

    @Autowired TaskService taskService;
    @Autowired ProjectService projectService;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
    @Autowired ProjectMembershipRepository memberships;

    @AfterEach
    void rejectsBeforeAnyRepositoryAccess() {
        verifyNoInteractions(tasks, projects, memberships);
    }

    @Test
    void validatesCreateRequestsAtServiceBoundary() {
        assertThrows(ConstraintViolationException.class,
            () -> taskService.create(ACCOUNT, 1L, new CreateTaskRequest("\u200b", null)));
        assertThrows(ConstraintViolationException.class,
            () -> projectService.create(ACCOUNT, new CreateProjectRequest("\u200b", "VALID", null)));
        assertThrows(ConstraintViolationException.class,
            () -> projectService.create(ACCOUNT, new CreateProjectRequest("Valid", " ", null)));
    }

    @Test
    void validatesUpdateRequestsAtServiceBoundary() {
        assertThrows(ConstraintViolationException.class,
            () -> taskService.update(ACCOUNT, 1L, 2L, new UpdateTaskRequest("\u200b", null, TaskStatus.PENDING)));
        assertThrows(ConstraintViolationException.class,
            () -> projectService.update(ACCOUNT, 1L, new UpdateProjectRequest("\u200b", null)));
    }

    @Test
    void rejectsNullRequests() {
        assertThrows(ConstraintViolationException.class, () -> taskService.create(ACCOUNT, 1L, null));
        assertThrows(ConstraintViolationException.class, () -> projectService.create(ACCOUNT, null));
        assertThrows(ConstraintViolationException.class, () -> taskService.update(ACCOUNT, 1L, 2L, null));
        assertThrows(ConstraintViolationException.class, () -> projectService.update(ACCOUNT, 1L, null));
    }

    @Test
    void rejectsMissingAccount() {
        assertThrows(ConstraintViolationException.class,
            () -> projectService.create(null, new CreateProjectRequest("Valid", "VALID", null)));
        assertThrows(ConstraintViolationException.class, () -> projectService.findAll(null, "true"));
        assertThrows(ConstraintViolationException.class, () -> projectService.findById(null, 1L));
        assertThrows(ConstraintViolationException.class,
            () -> projectService.update(null, 1L, new UpdateProjectRequest("Valid", null)));
    }
}
