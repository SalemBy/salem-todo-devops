package github.salemby.tododevops.identity.infrastructure.mail;

import github.salemby.tododevops.identity.application.VerificationTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** #71: the request thread never waits for the SMTP server, and a failed send is only logged. */
class SmtpVerificationMailerTests {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SmtpVerificationMailer mailer = new SmtpVerificationMailer(sender, new VerificationTokens(Duration.ofHours(24)),
        "no-reply@example.com", "http://example.com/verify");

    @AfterEach
    void stop() throws Exception {
        mailer.destroy();
    }

    @Test
    void sendReturnsBeforeTheServerAnswers() throws Exception {
        var release = new CountDownLatch(1);
        var sent = new CountDownLatch(1);
        doAnswer(call -> {
            release.await(10, TimeUnit.SECONDS);
            sent.countDown();
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        long start = System.nanoTime();
        mailer.send("ana@example.com", "token-value");
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1), "send waited for the SMTP server");
        release.countDown();
        assertTrue(sent.await(5, TimeUnit.SECONDS), "the email is still sent");
    }

    @Test
    void aFailedSendIsNotThrown() throws Exception {
        var attempted = new CountDownLatch(1);
        doAnswer(call -> {
            attempted.countDown();
            throw new MailSendException("down");
        }).when(sender).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> mailer.send("ana@example.com", "token-value"));
        assertTrue(attempted.await(5, TimeUnit.SECONDS));
    }
}
