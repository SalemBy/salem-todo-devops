package github.salemby.tododevops.support;

import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test-only encoder that can pause inside a hash, so a test can inspect what the caller holds meanwhile. */
public final class GatedPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder delegate;
    private final Semaphore parked = new Semaphore(0);
    private volatile Semaphore releases = new Semaphore(0);
    private volatile boolean armed;

    public GatedPasswordEncoder(PasswordEncoder delegate) { this.delegate = delegate; }

    public void arm() { releases = new Semaphore(0); parked.drainPermits(); armed = true; }

    public void awaitParked(Duration timeout) throws InterruptedException {
        assertTrue(parked.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS), "no password hash parked within " + timeout);
    }

    public void releaseOne() { releases.release(); }

    public void disarm() { armed = false; releases.release(Integer.MAX_VALUE / 2); }

    @Override public String encode(CharSequence raw) { gate(); return delegate.encode(raw); }

    @Override public boolean matches(CharSequence raw, String encoded) { gate(); return delegate.matches(raw, encoded); }

    @Override public boolean upgradeEncoding(String encoded) { return delegate.upgradeEncoding(encoded); }

    private void gate() {
        if (!armed) return;
        Semaphore current = releases;
        parked.release();
        try {
            if (!current.tryAcquire(30, TimeUnit.SECONDS)) throw new IllegalStateException("gated hash was never released");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
