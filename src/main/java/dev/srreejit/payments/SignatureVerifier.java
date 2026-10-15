package dev.srreejit.payments;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SignatureVerifier {
    private final String secret;
    private final Clock clock;
    public SignatureVerifier(String secret, Clock clock) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("webhook secret must contain at least 32 characters");
        this.secret = secret; this.clock = clock;
    }
    public void verify(byte[] body, String timestamp, String signature) {
        try {
            long sentAt = Long.parseLong(timestamp);
            long now = clock.instant().getEpochSecond();
            if (sentAt < now - 300 || sentAt > now + 300) throw new IllegalArgumentException();
            if (signature == null || !signature.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException();
            byte[] expected = HexFormat.of().parseHex(sign(secret, timestamp, body));
            if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { throw new DomainException(401, "invalid or expired webhook signature"); }
    }
    public static String sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8)); mac.update((byte) '.');
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("HMAC unavailable", ex); }
    }
}
