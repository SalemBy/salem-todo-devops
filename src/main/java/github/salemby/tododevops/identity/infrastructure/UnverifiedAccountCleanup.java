package github.salemby.tododevops.identity.infrastructure;

import github.salemby.tododevops.identity.application.EmailVerificationService;
import github.salemby.tododevops.shared.persistence.Timestamps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hourly removal of registrations nobody verified (#71). Every instance may run it; the delete is idempotent. */
@Component
class UnverifiedAccountCleanup {
    private static final Logger log = LoggerFactory.getLogger(UnverifiedAccountCleanup.class);
    private final EmailVerificationService verification;

    UnverifiedAccountCleanup(EmailVerificationService verification) {
        this.verification = verification;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    void run() {
        int deleted = verification.deleteStaleUnverified(Timestamps.now());
        if (deleted > 0) log.info("Deleted {} unverified registrations past their retention", deleted);
    }
}
