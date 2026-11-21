package github.salemby.tododevops.shared.validation;

import java.util.Locale;

/** The one email normalization rule: registration, login and lookups must all agree on it. */
public final class Emails {
    private Emails() {}

    /** Trims and lowercases; null stays null so validation can report it. */
    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
