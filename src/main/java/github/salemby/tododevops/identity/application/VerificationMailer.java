package github.salemby.tododevops.identity.application;

/** Sends the email that proves an address belongs to the person registering it (#71). */
public interface VerificationMailer {
    /** Sends the raw token to the address; the caller never logs or stores the raw token. */
    void send(String email, String token);
}
