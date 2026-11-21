package github.salemby.tododevops.identity.infrastructure.mail;

import github.salemby.tododevops.identity.application.VerificationMailer;
import github.salemby.tododevops.identity.application.VerificationTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends the verification link as plain text over SMTP: Mailpit locally, any SMTP server in deployment (#71). */
@Component
class SmtpVerificationMailer implements VerificationMailer {
    private final JavaMailSender sender;
    private final String from;
    private final String url;
    private final long hours;

    SmtpVerificationMailer(JavaMailSender sender, VerificationTokens tokens, @Value("${app.mail.from}") String from,
            @Value("${app.auth.verification-url}") String url) {
        this.sender = sender;
        this.from = from;
        this.url = url;
        this.hours = tokens.ttl().toHours();
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
        sender.send(message);
    }
}
