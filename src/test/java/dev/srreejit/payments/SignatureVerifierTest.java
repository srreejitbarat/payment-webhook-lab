package dev.srreejit.payments;

import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignatureVerifierTest {
    static final String SECRET = "test-only-webhook-secret-at-least-32-characters";
    final Clock clock = Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC);
    final SignatureVerifier verifier = new SignatureVerifier(SECRET, clock);
    final byte[] body = "{\"eventId\":\"demo\"}".getBytes(StandardCharsets.UTF_8);
    String timestamp(long delta) { return "" + (clock.instant().getEpochSecond() + delta); }
    @Test void acceptsValidSignature() {
        String ts=timestamp(0); assertDoesNotThrow(() -> verifier.verify(body,ts,SignatureVerifier.sign(SECRET,ts,body)));
    }
    @Test void detectsBodyTampering() {
        String ts=timestamp(0); String sig=SignatureVerifier.sign(SECRET,ts,body);
        assertThrows(DomainException.class, () -> verifier.verify("changed".getBytes(StandardCharsets.UTF_8),ts,sig));
    }
    @Test void rejectsExpiredAndFutureReplays() {
        for (long delta : new long[]{-301,301}) {
            String ts=timestamp(delta); assertThrows(DomainException.class, () -> verifier.verify(body,ts,SignatureVerifier.sign(SECRET,ts,body)));
        }
    }
    @Test void rejectsMissingMalformedAndOverflowingHeaders() {
        for (String ts : new String[]{null,"abc","9999999999999999999999999",Long.toString(Long.MAX_VALUE)})
            assertThrows(DomainException.class, () -> verifier.verify(body,ts,"00".repeat(32)));
        assertThrows(DomainException.class, () -> verifier.verify(body,timestamp(0),null));
        assertThrows(DomainException.class, () -> verifier.verify(body,timestamp(0),"not-hex"));
    }
    @Test void rejectsWrongKeyAndWeakConfiguration() {
        String ts=timestamp(0);
        assertThrows(DomainException.class, () -> verifier.verify(body,ts,SignatureVerifier.sign("wrong-secret",ts,body)));
        assertThrows(IllegalArgumentException.class, () -> new SignatureVerifier("short",clock));
    }
}
