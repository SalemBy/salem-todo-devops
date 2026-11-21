package github.salemby.tododevops.identity.infrastructure.mail;

import github.salemby.tododevops.identity.application.VerificationMailer;
import github.salemby.tododevops.identity.application.VerificationTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Sends the verification link as plain text over SMTP: Mailpit locally, any SMTP server in deployment (#71).
 * Delivery runs on two background threads, so no request waits for the mail server, and the time a request takes
 * does not reveal whether an email was sent. A failed or dropped email is only logged: the owner can ask again.
 */
@Component
class SmtpVerificationMailer implements VerificationMailer, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationMailer.class);
    private final JavaMailSender sender;
    private final String from;
    private final String url;
    private final long hours;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    SmtpVerificationMailer(JavaMailSender sender, VerificationTokens tokens, @Value("${app.mail.from}") String from,
            @Value("${app.auth.verification-url}") String url) {
        this.sender = sender;
        this.from = from;
        this.url = url;
        this.hours = tokens.ttl().toHours();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("verification-mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
    }

    @Override
    public void send(String email, String token) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Verify your email address");
        message.setText("Confirm that this address belongs to you by opening this link:\n\n"
            + url + "?token=" + token + "\n\n"
            + "This link expires in " + hours + " hours. If you did not create an account, ignore this email.\n");
        try {
            executor.execute(() -> deliver(message));
        } catch (TaskRejectedException ex) {
            log.warn("Verification email queue is full; one email was not sent");
        }
    }

    private void deliver(SimpleMailMessage message) {
        try {
            sender.send(message);
        } catch (MailException ex) {
            log.warn("Could not send a verification email: {}", ex.getMessage());
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
